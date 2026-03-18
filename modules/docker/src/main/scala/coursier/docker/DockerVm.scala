package coursier.docker

import com.github.plokhotnyuk.jsoniter_scala.core.writeToArray
import coursier.cache.{DigestBasedCache, FileCache}
import coursier.util.Task
import coursier.docker.vm.Vm
import io.github.alexarchambault.isterminal.IsTerminal

import java.util.UUID
import com.jcraft.jsch.Session

object DockerVm {

  private class Pull(
    workDir: os.Path,
    cache: FileCache[Task],
    digestCacheOpt: Option[DigestBasedCache[Task]],
    vm: Vm,
    session: Session,
    debug: Boolean
  ) {

    val randomUuid = UUID.randomUUID()

    def toVmPath(path: os.Path): os.SubPath =
      vm.params.mounts
        .collectFirst {
          case mount if path.startsWith(mount.hostPath) =>
            mount.guestPath / path.relativeTo(mount.hostPath).asSubPath
        }
        .getOrElse {
          sys.error(s"Cannot adapt local path $path to VM (not in known mount points)")
        }
    def toVmPathStr(path: os.Path): String =
      "/" + toVmPath(path).toString

    val vmPriviledgedArchiveCachePath = os.sub / "cs/parc"

    def vmUnpackPathFor(archive: os.Path): os.SubPath = {
      val archiveSubPath =
        if (archive.startsWith(os.Path(cache.location)))
          archive.relativeTo(os.Path(cache.location)).asSubPath
        else
          digestCacheOpt match {
            case Some(digestCache) if archive.startsWith(os.Path(digestCache.location)) =>
              os.sub / "digest" / archive.relativeTo(os.Path(digestCache.location)).asSubPath
            case _ =>
              ???
          }
      vmPriviledgedArchiveCachePath / archiveSubPath
    }

    def ifDebug(s: => String): String =
      if (debug) s else ""

    def unpackLayerScript(archive: os.Path): String = {
      val vmArchivePath = toVmPath(archive)
      val dest          = vmUnpackPathFor(archive)
      // format: off
      s"""#!/usr/bin/env bash
         |set -e
         |
         |if [ ! -d "/$vmPriviledgedArchiveCachePath" ]; then
         |  mkdir -p "/$vmPriviledgedArchiveCachePath"
         |fi
         |
         |chown root:root "/$vmPriviledgedArchiveCachePath"
         |
         |DEST="/$dest"
         |
         |if [ -d "$$DEST" ]; then${ifDebug("\n" + s"""echo "$$DEST already exists, no need to unpack /$vmArchivePath" 1>&2""")}
         |  exit 0
         |fi
         |
         |TMP_DEST="/${dest / os.up}/.${dest.last}.$randomUuid"
         |mkdir -p "$$TMP_DEST"
         |
         |# TODO trap thing to clean-up $$TMP_DEST
         |
         |cd "$$TMP_DEST"${ifDebug("\n" + s"""echo "Unpacking /$vmArchivePath" 1>&2""")}
         |tar -zxf "/$vmArchivePath"
         |
         |if [ -d "$$DEST" ]; then
         |  echo "$$DEST created concurrently" 1>&2
         |  rm -rf "$$TMP_DEST"
         |  exit 0
         |fi
         |
         |# FIXME Can we ensure this is atomic?
         |# FIXME Can we ensure $$DEST isn't created in the mean time and we don't end up as a sub-directory of it?
         |mv "$$TMP_DEST" "$$DEST"${ifDebug("\n" + s"""echo "Unpacked /$vmArchivePath under $$DEST" 1>&2""")}
         |""".stripMargin
      // format: on
    }

    def runAsSudoViaWorkDir(name: String, script: String): Unit = {
      val scriptPath = workDir / s"$name.sh"
      os.write(scriptPath, script)
      Vm.runCommand(session)("sudo", "bash", toVmPathStr(scriptPath))
      os.remove(scriptPath)
    }

  }

  private class Run(
    workDir: os.Path,
    cache: FileCache[Task],
    digestCache: DigestBasedCache[Task],
    layerFiles: Seq[os.Path],
    rootFsDirName: String,
    vm: Vm,
    session: Session,
    debug: Boolean
  ) extends Pull(
        workDir,
        cache,
        Some(digestCache),
        vm,
        session,
        debug
      ) {

    val vmUnionFsWorkDir = os.sub / "overlays" / randomUuid.toString

    def makeUnionFsScript(): String = {
      val bt        = "\\"
      val lowerDirs = layerFiles.map(vmUnpackPathFor).map("/" + _)
      val arg       = s""""lowerdir=$lowerDirs,upperdir=$$WORKDIR/upper,workdir=$$WORKDIR/work""""

      val maxGroupSize = 2048
      def groups(
        acc: List[List[String]],
        input: List[String],
        current: List[String],
        currentSize: Int
      ): List[List[String]] =
        input match {
          case Nil =>
            if (current.isEmpty) acc.reverse
            else groups(current.reverse :: acc, input, Nil, 0)
          case h :: t =>
            assert(h.length <= maxGroupSize)
            if (currentSize + h.length > maxGroupSize)
              groups(current.reverse :: acc, input, Nil, 0)
            else
              groups(acc, t, h :: current, h.length + currentSize)
        }

      val groups0 = groups(Nil, lowerDirs.toList, Nil, 0).reverse

      def groupScript(elems: Seq[String], index: Int, isLast: Boolean): String = {
        val suffixIfNotLast = if (isLast) "" else s"-$index"
        // format: off
        s"""
           |mkdir -p "$$WORKDIR/upper$suffixIfNotLast"
           |mkdir -p "$$WORKDIR/work-$index"
           |mkdir -p "$$WORKDIR/$rootFsDirName$suffixIfNotLast"${ifDebug("\n" + s"""echo "Creating overlay file system $index" 1>&2""")}
           |mount $bt
           |  -o "lowerdir=${elems.mkString(":")},upperdir=$$WORKDIR/upper$suffixIfNotLast,workdir=$$WORKDIR/work-$index" $bt
           |  -t overlay $bt
           |  overlay "$$WORKDIR/$rootFsDirName$suffixIfNotLast"
           |""".stripMargin
        // format: on
      }

      val scriptPart = groups0
        .zipWithIndex
        .map {
          case (group, groupIdx) =>
            groupScript(group, groupIdx, groupIdx == groups0.length - 1)
        }
        .mkString

      s"""#!/usr/bin/env bash
         |set -eu
         |WORKDIR="/$vmUnionFsWorkDir"
         |""".stripMargin + scriptPart
    }

    def cleanUpUnionFsScript(): String =
      s"""#!/usr/bin/env bash
         |set -e
         |WORKDIR="/overlays/$randomUuid"
         |sudo umount "$$WORKDIR/$rootFsDirName"
         |sudo rm -rf "$$WORKDIR/upper"
         |sudo rm -rf "$$WORKDIR/work"
         |""".stripMargin

    def withUnionFs[T](f: os.SubPath => T): T = {
      runAsSudoViaWorkDir("make-fs", makeUnionFsScript())
      try f(os.sub / "overlays" / randomUuid.toString / "upper")
      finally
        runAsSudoViaWorkDir("unmount-fs", cleanUpUnionFsScript())
    }

    def runcScript(
      runcFile: os.Path,
      configFile: os.Path,
      keepArchive: Option[(os.SubPath, os.Path)]
    ): String = {
      val header =
        s"""#!/usr/bin/env bash
           |set -e
           |cd "/$vmUnionFsWorkDir"
           |cp "${toVmPathStr(configFile)}" config.json
           |""".stripMargin
      val mainCommand = s"${toVmPathStr(runcFile)} run cs-test-container"
      val mainContent = keepArchive match {
        case Some((vmDir, archiveDest)) =>
          val archiveDestInVm = toVmPath(archiveDest)
          // format: off
          s"""set +e
             |$mainCommand
             |EXIT_CODE=$$?
             |set -e${ifDebug("\n" + s"""ls "/${archiveDestInVm / os.up}"""")}
             |if [ -z "$$(ls -A "/$vmDir" 2>/dev/null)" ]; then
             |  ${if (debug) s"""echo "No new files" 1>&2""" else ":"}
             |else
             |  ( cd "/$vmDir" && shopt -s dotglob nullglob && tar --sort=name --mtime='1970-01-01 00:00:00' -cvf "/$archiveDestInVm" * )${ifDebug("\n  " + s"""ls -lh "/$archiveDestInVm"""")}
             |fi
             |exit "$$EXIT_CODE"
             |""".stripMargin
          // format: on
        case None =>
          s"exec $mainCommand" + "\n"
      }
      header + mainContent
    }

  }

  def pullContainer(
    workDir: os.Path,
    cache: FileCache[Task],
    layerFiles: Seq[os.Path],
    vm: Vm,
    session: Session,
    debug: Boolean = false
  ): Unit = {

    val pull = new Pull(
      workDir,
      cache,
      None,
      vm,
      session,
      debug
    )

    for (layerFile <- layerFiles)
      pull.runAsSudoViaWorkDir("unpack", pull.unpackLayerScript(layerFile))
  }

  def runContainer(
    workDir: os.Path,
    cache: FileCache[Task],
    digestCache: DigestBasedCache[Task],
    layerFiles: Seq[os.Path],
    rootFsDirName: String,
    runcFile: os.Path,
    configPath: os.Path,
    vm: Vm,
    session: Session,
    stdout: os.ProcessOutput = os.Inherit,
    withUpperDirArchive: Option[os.Path => Unit] = None,
    debug: Boolean = false
  ): os.CommandResult = {

    val run = new Run(
      workDir,
      cache,
      digestCache,
      layerFiles,
      rootFsDirName,
      vm,
      session,
      debug = debug
    )

    for (layerFile <- layerFiles)
      run.runAsSudoViaWorkDir("unpack", run.unpackLayerScript(layerFile))

    val runcScriptPath = workDir / "main.sh"

    run.withUnionFs { upperDirInVm =>
      val newLayerArchive = workDir / "upper.tar"
      val keepArchive =
        if (withUpperDirArchive.isEmpty) None
        else Some((upperDirInVm, newLayerArchive))
      os.write(runcScriptPath, run.runcScript(runcFile, configPath, keepArchive))
      val useTerm = stdout != os.Pipe && IsTerminal.isTerminal()
      val cmd     = Seq[os.Shellable]("sudo", "bash", run.toVmPathStr(runcScriptPath))
      try
        if (useTerm)
          withRawTerm {
            Vm.runCommand(session, stdout = stdout, check = false, pty = true)(cmd: _*)
          }
        else
          Vm.runCommand(session, stdout = stdout, check = false)(cmd: _*)
      finally
        for (f <- withUpperDirArchive if os.exists(newLayerArchive))
          f(newLayerArchive)
    }
  }

  private def withRawTerm[T](f: => T): T = {
    val saved = os.proc("stty", "-g")
      .call(stdin = os.InheritRaw, stdout = os.Pipe, stderr = os.InheritRaw)
      .out.text()
    try {
      os.proc("stty", "raw")
        .call(stdin = os.InheritRaw, stdout = os.InheritRaw, stderr = os.InheritRaw)
      f
    }
    finally
      os.proc("stty", saved)
        .call(stdin = os.InheritRaw, stdout = os.InheritRaw, stderr = os.InheritRaw)
  }

}
