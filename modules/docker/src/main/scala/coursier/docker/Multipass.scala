package coursier.docker

import com.github.plokhotnyuk.jsoniter_scala.core.writeToArray
import coursier.cache.{DigestBasedCache, FileCache}
import coursier.util.Task
import io.github.alexarchambault.isterminal.IsTerminal

import java.util.UUID

object Multipass {

  private class Pull(
    workDir: os.Path,
    cache: FileCache[Task],
    digestCacheOpt: Option[DigestBasedCache[Task]],
    layerFiles: Seq[os.Path],
    multiPassVmName: String
  ) {

    val randomUuid = UUID.randomUUID()

    val mounts = Seq(
      // os.Path(priviledgedArchiveCache.location) -> os.sub / "cs" / "parc",
      // os.Path(archiveCache.location) -> os.sub / "cs" / "arc",
      os.Path(cache.location) -> os.sub / "cs" / "v1",
      workDir                 -> os.sub / "workdir" / randomUuid.toString
    ) ++
      digestCacheOpt.toSeq.map { digestCache =>
        os.Path(digestCache.location) -> os.sub / "cs" / "digest"
      }
    def toVmPath(path: os.Path): os.SubPath =
      mounts
        .collectFirst {
          case (localDir, pathInVm) if path.startsWith(localDir) =>
            pathInVm / path.relativeTo(localDir).asSubPath
        }
        .getOrElse {
          sys.error(s"Cannot adapt local path $path to VM (not in known mount points)")
        }
    def toVmPathStr(path: os.Path): String =
      "/" + toVmPath(path).toString

    def withMultiPassMount[T](localDir: os.Path, vmPath: os.SubPath)(f: => T): T = {
      val mountPoint     = s"$multiPassVmName:/$vmPath"
      val mountCommand   = os.proc("multipass", "mount", localDir, mountPoint)
      val unmountCommand = os.proc("multipass", "umount", mountPoint)
      val cleanUpCommand = os.proc(
        "multipass",
        "exec",
        "-n",
        multiPassVmName,
        "--",
        "sudo",
        "rmdir", /* "-p", */ s"/$vmPath"
      )
      try {
        mountCommand.call(stdin = os.Inherit, stdout = os.Inherit)
        f
      }
      finally {
        unmountCommand.call(stdin = os.Inherit, stdout = os.Inherit)
        cleanUpCommand.call(stdin = os.Inherit, stdout = os.Inherit, check = false)
      }
    }

    def withMultiPassMounts[T](mounts: List[(os.Path, os.SubPath)])(f: => T): T =
      mounts match {
        case Nil => f
        case (local, vm) :: others =>
          withMultiPassMount(local, vm) {
            withMultiPassMounts(others)(f)
          }
      }

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

    def unpackLayerScript(archive: os.Path): String = {
      val vmArchivePath = toVmPath(archive)
      val dest          = vmUnpackPathFor(archive)
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
         |if [ -d "$$DEST" ]; then
         |  echo "$$DEST already exists, no need to unpack /$vmArchivePath" 1>&2
         |  exit 0
         |fi
         |
         |TMP_DEST="/${dest / os.up}/.${dest.last}.$randomUuid"
         |mkdir -p "$$TMP_DEST"
         |
         |# TODO trap thing to clean-up $$TMP_DEST
         |
         |cd "$$TMP_DEST"
         |echo "Unpacking /$vmArchivePath" 1>&2
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
         |mv "$$TMP_DEST" "$$DEST"
         |echo "Unpacked /$vmArchivePath under $$DEST" 1>&2
         |""".stripMargin
    }

    def exec(cmd: os.Shellable*): os.proc =
      os.proc("multipass", "exec", "-n", multiPassVmName, "--", cmd)

    def runAsSudoViaWorkDir(name: String, script: String): Unit = {
      val scriptPath = workDir / s"$name.sh"
      os.write(scriptPath, script)
      exec("sudo", "bash", toVmPathStr(scriptPath))
        .call(stdin = os.Inherit, stdout = os.Inherit)
      os.remove(scriptPath)
    }

  }

  private class Run(
    workDir: os.Path,
    cache: FileCache[Task],
    digestCache: DigestBasedCache[Task],
    layerFiles: Seq[os.Path],
    rootFsDirName: String,
    multiPassVmName: String
  ) extends Pull(
        workDir,
        cache,
        Some(digestCache),
        layerFiles,
        multiPassVmName
      ) {

    val vmUnionFsWorkDir = os.sub / "overlays" / randomUuid.toString

    def makeUnionFsScript(): String = {
      val bt        = "\\"
      val lowerDirs = layerFiles.map(vmUnpackPathFor).map("/" + _).mkString(":")
      s"""#!/usr/bin/env bash
         |set -e
         |
         |WORKDIR="/$vmUnionFsWorkDir"
         |
         |mkdir -p "$$WORKDIR/upper"
         |mkdir "$$WORKDIR/work"
         |mkdir "$$WORKDIR/$rootFsDirName"
         |
         |echo "Creating overlay file system" 1>&2
         |mount -t overlay overlay $bt
         |  -o "lowerdir=$lowerDirs,upperdir=$$WORKDIR/upper,workdir=$$WORKDIR/work" $bt
         |  "$$WORKDIR/$rootFsDirName"
         |""".stripMargin
    }

    def cleanUpUnionFsScript(): String =
      s"""#!/usr/bin/env bash
         |set -e
         |
         |WORKDIR="/overlays/$randomUuid"
         |
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
           |
           |cd "/$vmUnionFsWorkDir"
           |cp "${toVmPathStr(configFile)}" config.json
           |""".stripMargin
      val mainCommand = s"${toVmPathStr(runcFile)} run cs-test-container"
      val mainContent = keepArchive match {
        case Some((vmDir, archiveDest)) =>
          val archiveDestInVm = toVmPath(archiveDest)
          // FIXME I'd like to avoid passing '.' to tar here
          s"""set +e
             |$mainCommand
             |EXIT_CODE=$$?
             |set -e
             |ls "/${archiveDestInVm / os.up}"
             |if [ -z "$$(ls -A "/$vmDir" 2>/dev/null)" ]; then
             |  echo "No new files" 1>&2
             |else
             |  ( cd "/$vmDir" && shopt -s dotglob nullglob && tar --sort=name --mtime='@0' -cvf "/$archiveDestInVm" * )
             |  ls -lh "/$archiveDestInVm"
             |fi
             |exit "$$EXIT_CODE"
             |""".stripMargin
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
    multiPassVmName: String
  ): Unit = {

    val pull = new Pull(
      workDir,
      cache,
      None,
      layerFiles,
      multiPassVmName
    )

    pull.withMultiPassMounts(pull.mounts.toList) {
      for (layerFile <- layerFiles)
        pull.runAsSudoViaWorkDir("unpack", pull.unpackLayerScript(layerFile))
    }
  }

  def runContainer(
    workDir: os.Path,
    cache: FileCache[Task],
    digestCache: DigestBasedCache[Task],
    layerFiles: Seq[os.Path],
    rootFsDirName: String,
    runcFile: os.Path,
    configPath: os.Path,
    multiPassVmName: String,
    stdout: os.ProcessOutput = os.Inherit,
    withUpperDirArchive: Option[os.Path => Unit] = None
  ): os.CommandResult = {

    val run = new Run(
      workDir,
      cache,
      digestCache,
      layerFiles,
      rootFsDirName,
      multiPassVmName
    )

    run.withMultiPassMounts(run.mounts.toList) {
      for (layerFile <- layerFiles)
        run.runAsSudoViaWorkDir("unpack", run.unpackLayerScript(layerFile))

      val runcScriptPath = workDir / "main.sh"

      run.withUnionFs { upperDirInVm =>
        val newLayerArchive = workDir / "upper.tar"
        val keepArchive =
          if (withUpperDirArchive.isEmpty) None
          else Some((upperDirInVm, newLayerArchive))
        os.write(runcScriptPath, run.runcScript(runcFile, configPath, keepArchive))
        try
          run.exec("sudo", "bash", run.toVmPathStr(runcScriptPath))
            .call(stdin = os.Inherit, stdout = stdout, check = false)
        finally
          for (f <- withUpperDirArchive if os.exists(newLayerArchive))
            f(newLayerArchive)
      }
    }
  }

}
