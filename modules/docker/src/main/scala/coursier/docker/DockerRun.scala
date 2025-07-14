package coursier.docker

import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, writeToArray}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import coursier.cache.{ArchiveCache, DigestBasedCache, FileCache}
import coursier.exec.Execve
import coursier.util.{Artifact, Task}
import io.github.alexarchambault.isterminal.IsTerminal

import java.lang.management.ManagementFactory
import java.nio.file.FileSystems
import java.nio.file.attribute.PosixFilePermission

import scala.util.Properties
import coursier.docker.vm.Vm
import java.util.UUID

object DockerRun {

  class MaybeSudo(useSudo: Boolean) { maybeSudo =>

    private def sudo(command: os.Shellable*): Unit =
      sudoWith(command: _*)()

    def sudoWith(command: os.Shellable*)(
      cwd: os.Path = null,
      check: Boolean = true,
      stdout: os.ProcessOutput = os.Inherit,
      stderr: os.ProcessOutput = os.Inherit
    ): os.CommandResult = {
      val proc = if (useSudo) os.proc("sudo", command) else os.proc(command)
      if (useSudo)
        System.err.println(
          proc.command.flatMap(_.value)
            .map(value =>
              if (value.contains(" ") || value.contains("\"")) "\"" + value + "\"" else value
            )
            .mkString(" ")
        )
      proc.call(
        stdin = os.Inherit,
        stdout = stdout,
        stderr = stderr,
        cwd = cwd,
        check = check
      )
    }

    def apply(command: os.Shellable*): Unit =
      sudo(command: _*)

    object makeDir {
      object all {
        def apply(dir: os.Path): Unit =
          if (useSudo)
            sudo("mkdir", "-p", dir)
          else
            os.makeDir.all(dir)
      }
    }
    object copy {
      def apply(from: os.Path, to: os.Path, copyAttributes: Boolean = false): Unit =
        if (useSudo)
          sudo("cp", "-a", from, to)
        else
          os.copy(from, to, copyAttributes = copyAttributes)
    }
    object remove {
      object all {
        def apply(target: os.Path): Unit =
          if (useSudo)
            sudo("rm", "-rf", target)
          else
            os.remove.all(target)
      }
    }
    object perms {
      private def octal(perms: os.PermSet): String = {
        def helper(read: Boolean, write: Boolean, exec: Boolean): Int = {
          var value = 0
          if (read) value += 4
          if (write) value += 2
          if (exec) value += 1
          value
        }
        val user = helper(
          perms.contains(PosixFilePermission.OWNER_READ),
          perms.contains(PosixFilePermission.OWNER_WRITE),
          perms.contains(PosixFilePermission.OWNER_EXECUTE)
        )
        val group = helper(
          perms.contains(PosixFilePermission.GROUP_READ),
          perms.contains(PosixFilePermission.GROUP_WRITE),
          perms.contains(PosixFilePermission.GROUP_EXECUTE)
        )
        val other = helper(
          perms.contains(PosixFilePermission.OTHERS_READ),
          perms.contains(PosixFilePermission.OTHERS_WRITE),
          perms.contains(PosixFilePermission.OTHERS_EXECUTE)
        )
        s"$user$group$other"
      }
      object set {
        def apply(path: os.Path, perms: os.PermSet, recursive: Boolean = false): Unit =
          if (useSudo)
            sudo("chmod", if (recursive) Seq("-R") else Nil, octal(perms), path)
          else {
            os.perms.set(path, perms)
            if (recursive && os.isDir(path))
              for (elem <- os.walk(path))
                os.perms.set(elem, perms)
          }
      }
    }
    object owner {
      object set {
        def apply(path: os.Path, user: String, group: String, recursive: Boolean = false): Unit =
          if (useSudo)
            sudo("chown", if (recursive) Seq("-R") else Nil, s"$user:$group", path)
          else {
            val lookupService = FileSystems.getDefault.getUserPrincipalLookupService
            val user0         = lookupService.lookupPrincipalByName(user)
            val group0        = lookupService.lookupPrincipalByGroupName(group)
            os.owner.set(path, user0)
            os.group.set(path, group)
            if (recursive && os.isDir(path))
              for (elem <- os.walk(path)) {
                os.owner.set(elem, user0)
                os.group.set(elem, group)
              }
          }
      }
    }
    object list {
      def apply(path: os.Path): IndexedSeq[os.Path] =
        if (useSudo)
          sudoWith("ls", "-a", path)(stdout = os.Pipe)
            .out
            .lines()
            .filter(line => line != "." && line != "..")
            .sorted
            .map(path / _)
        else
          os.list(path)
    }
    object write {
      def apply(target: os.Path, data: os.Source, perms: os.PermSet = null): Unit =
        if (useSudo) {
          val tmpFile = os.temp(data, perms = "rw-------")
          sudo("mv", tmpFile, target)
          if (perms != null)
            maybeSudo.perms.set(target, perms)
        }
        else
          os.write(target, data, perms = perms)
    }
  }

  def defaultContainerName = {
    val pid = ManagementFactory.getRuntimeMXBean.getName.takeWhile(_ != '@').toInt
    s"cs-container-$pid"
  }

  private final case class Process(
    cwd: String,
    args: Seq[String]
  )

  private lazy val processCodec: JsonValueCodec[Process] =
    JsonCodecMaker.make

  def run(
    cache: FileCache[Task],
    digestCache: DigestBasedCache[Task],
    config: DockerImageConfig.Config,
    layerFiles: () => Seq[os.Path],
    layerDirs: () => Seq[os.Path],
    containerName: String = defaultContainerName,
    vmOpt: Option[Vm] = None,
    rootFsDirName: String = "rootfs",
    interactive: Boolean = IsTerminal.isTerminal(),
    useSudo: Boolean = true,
    withUpperDir: Option[os.Path => Unit] = None,
    withUpperDirArchive: Option[os.Path => Unit] = None,
    useExec: Boolean = false,
    stdout: os.ProcessOutput = null,
    debug: Boolean = false
  ): os.CommandResult = {

    val maybeSudo = new MaybeSudo(useSudo)

    val runtime = os.Path(Runc.runc(cache))

    val runcConfig0 = Runc.config(
      false,
      env = config.Env,
      cwd = config.WorkingDir.getOrElse(""),
      cmd = config.Cmd,
      rootFsDirName
    )

    val workDir = vmOpt match {
      case Some(vm) =>
        val supportsPerms = FileSystems.getDefault.supportedFileAttributeViews().contains("posix")
        val perms: os.PermSet = if (supportsPerms) "rwx------" else null
        os.temp.dir(prefix = "cs-docker-pull", perms = perms)
        vm.params.hostWorkDir / UUID.randomUUID().toString
      case None =>
        val path = os.root / "var/lib/coursier/overlays" / defaultContainerName
        if (os.exists(path))
          os.remove.all(path) // FIXME Small chance there might mounts under there…
        path
    }

    val configPath = workDir / "config.json"

    vmOpt match {
      case Some(vm) =>
        try {
          os.write(configPath, writeToArray(runcConfig0), createFolders = true)
          vm.withSession { session =>
            DockerVm.runContainer(
              workDir,
              cache,
              digestCache,
              layerFiles(),
              rootFsDirName,
              runtime,
              configPath,
              vm,
              session,
              if (stdout == null) os.Inherit else stdout,
              withUpperDirArchive
            )
          }
        }
        finally
          os.remove.all(workDir)
      case None =>
        try {
          val tmpConfigFile = os.temp(
            writeToArray(runcConfig0),
            prefix = "runc-",
            suffix = ".json",
            perms = "rw-------"
          )
          maybeSudo.makeDir.all(configPath / os.up)
          maybeSudo.copy(tmpConfigFile, configPath)

          maybeSudo.write(
            workDir / "process.json",
            writeToArray(
              Process(
                config.WorkingDir.getOrElse("/"),
                config.Cmd
              )
            )(processCodec)
          )

          val upper          = workDir / "upper"
          val overlayWorkdir = workDir / "workdir"
          val rootfs         = workDir / rootFsDirName

          maybeSudo.makeDir.all(upper)
          maybeSudo.makeDir.all(overlayWorkdir)
          maybeSudo.makeDir.all(rootfs)

          if (debug)
            System.err.println("Creating overlay file system")

          val lowerDirs = layerDirs().map(_.toString).mkString(":")
          maybeSudo(
            "mount",
            "-t",
            "overlay",
            "overlay",
            "-o",
            s"lowerdir=$lowerDirs,upperdir=$upper,workdir=$overlayWorkdir",
            s"$rootfs"
          )
          try
            if (useExec) {
              val sudo           = if (useSudo) "sudo " else ""
              val interactiveOpt = if (interactive) " -t" else ""
              val script         =
                s"""#!/usr/bin/env sh
                   |set -ex
                   |cleanUp() {
                   |  $sudo"$runtime" delete -f "$defaultContainerName" 2>/dev/null || true
                   |  ${sudo}umount "$rootfs" && ${sudo}rm -rf "$upper" && ${sudo}rm -rf "$overlayWorkdir"
                   |}
                   |trap cleanUp EXIT
                   |cd "$workDir"
                   |$sudo"$runtime" create "$defaultContainerName"
                   |set +e
                   |$sudo"$runtime" exec$interactiveOpt -p "$workDir/process.json" "$defaultContainerName"
                   |EXIT_CODE=$$?
                   |set -e
                   |exit $$EXIT_CODE
                   |""".stripMargin
              val scriptPath = workDir / "run.sh"
              maybeSudo.write(scriptPath, script, perms = "rwxr-xr-x")
              Execve.execve(
                scriptPath.toString,
                Array(scriptPath.toString),
                sys.env
                  .iterator
                  .map {
                    case (k, v) =>
                      s"$k=$v"
                  }
                  .toArray
                  .sorted
              )
              sys.error("Should not happen")
            }
            else {
              val res = {
                val sudo           = if (useSudo) "sudo " else ""
                val interactiveOpt = if (interactive) " -t" else ""
                val script         =
                  s"""#!/usr/bin/env sh
                     |set -eu
                     |cleanUp() {
                     |  $sudo"$runtime" delete -f "$defaultContainerName" 2>/dev/null || true
                     |}
                     |trap cleanUp EXIT
                     |$sudo"$runtime" create "$defaultContainerName"
                     |$sudo"$runtime" exec$interactiveOpt -p "$workDir/process.json" "$defaultContainerName"
                     |echo $$?
                     |EXIT_CODE=$$?
                     |echo "Exit code is $$EXIT_CODE" 1>&2
                     |exit $$EXIT_CODE
                     |""".stripMargin
                val scriptPath = workDir / "run.sh"
                maybeSudo.write(scriptPath, script, perms = "rwxr-xr-x")
                os.proc(scriptPath).call(
                  cwd = workDir,
                  check = false,
                  stdin = if (interactive) os.InheritRaw else os.Inherit,
                  stdout =
                    if (stdout == null) if (interactive) os.InheritRaw else os.Inherit else stdout,
                  stderr = if (interactive) os.InheritRaw else os.Inherit
                )
              }
              if (res.exitCode == 0)
                for (f <- withUpperDir)
                  f(upper)
              res
            }
          finally {
            maybeSudo.sudoWith(runtime, "delete", "-f", defaultContainerName)(
              check = false,
              stdout = os.Pipe,
              stderr = os.Pipe
            )
            maybeSudo("umount", s"$rootfs")

            maybeSudo.remove.all(upper)
            maybeSudo.remove.all(overlayWorkdir)
          }
        }
        finally
          maybeSudo.remove.all(workDir)
    }
  }

}
