// Vendored from mill-native-image with Mill 1.2 support
package io.github.alexarchambault.millnativeimage

import java.io.File
import java.nio.charset.Charset
import mill.*
import mill.api.*

import scala.util.Properties

trait NativeImage extends Module {
  import NativeImage.*

  def nativeImagePersist: Boolean            = false
  def nativeImageUseJpms: T[Option[Boolean]] =
    Task(None)

  @deprecated("Not used anymore by mill-native-image", "0.2.6")
  def nativeImageCsCommand: T[Seq[String]] = Task {
    Seq(systemCs)
  }

  def nativeImageGraalVmJvmId: T[String] = Task {
    s"graalvm-java17:$defaultGraalVmVersion"
  }

  /**
   * GraalVM home directory - not used when generating images from docker
   *
   * Beware: only `nativeImageGraalVmJvmId` is used when generating images from
   * docker, although this task is evaluated anyway.
   */
  def nativeImageGraalvmHome: T[PathRef] = Task {
    val f = () => {
      val strPath = Task.env.get("GRAALVM_HOME").getOrElse {
        import coursier.cache.{ArchiveCache, FileCache}
        import coursier.jvm.{JavaHome => CsJavaHome, JvmCache}
        import coursier.util.{Task => CsTask}
        import scala.concurrent.duration.Duration

        val csCache      = FileCache[CsTask]().withTtl(Duration.Zero)
        val archiveCache = ArchiveCache().withCache(csCache)
        val jvmCache     = JvmCache().withArchiveCache(archiveCache).withIndex(jvmIndex)
        val javaHome     = CsJavaHome().withCache(jvmCache).withUpdate(true)
        javaHome.get(nativeImageGraalVmJvmId())
          .unsafeRun(wrapExceptions = true)(
            using csCache.ec
          )
          .getAbsolutePath
      }
      PathRef(os.Path(strPath), quick = true)
    }
    if generateNativeImageWithFileSystemChecker then f()
    else
      BuildCtx.withFilesystemCheckerDisabled {
        System.err.println("nativeImageGraalvmHome: skipping Mill file system checker")
        f()
      }
  }

  def nativeImageClassPath: T[Seq[PathRef]]
  def nativeImageMainClass: T[String]
  def nativeImageOptions:   T[Seq[String]] = Task {
    Seq.empty[String]
  }

  def nativeImageName: T[String] = Task {
    "launcher"
  }

  def nativeImageDockerParams: T[Option[DockerParams]] = Task {
    Option.empty[DockerParams]
  }
  def nativeImageDockerWorkingDir: T[os.Path] = Task {
    Task.dest / "working-dir"
  }

  def nativeImageUseManifest: T[Boolean] = Task {
    Properties.isWin && nativeImageDockerParams().isEmpty
  }

  /**
   * Setting this to false allows is equivalent to --no-filesystem-checker for
   * native image generation
   */
  def generateNativeImageWithFileSystemChecker: Boolean = true

  def nativeImageScript(imageDest: String = ""): Task.Command[PathRef] = Task.Command {
    val imageDestOpt    = if imageDest.isEmpty then None else Some(os.Path(imageDest, BuildCtx.workspaceRoot))
    val cp              = nativeImageClassPath().map(_.path)
    val mainClass0      = nativeImageMainClass()
    val nativeImageDest = {
      val dir = Task.dest
      val str = dir.toString
      val idx = str.lastIndexOf("nativeImageScript")
      if idx < 0 then {
        System.err.println(s"Something went wrong, cannot find nativeImageScript in path $str")
        dir
      } else {
        val updated = str.take(idx) + "nativeImage" + str.drop(idx + "nativeImageScript".length)
        os.Path(updated)
      }
    }
    val dest       = nativeImageDest / nativeImageName()
    val actualDest = nativeImageDest / (nativeImageName() + platformExtension)

    val (command, tmpDestOpt, extraEnv) = generateNativeImage(
      graalVmHome = nativeImageGraalvmHome().path,
      jvmId = nativeImageGraalVmJvmId(),
      classPath = cp,
      mainClass = mainClass0,
      dest = dest,
      nativeImageOptions = nativeImageOptions(),
      dockerParamsOpt = nativeImageDockerParams(),
      dockerWorkingDir = nativeImageDockerWorkingDir(),
      createManifest = nativeImageUseManifest(),
      workingDir = Task.dest / "working-dir",
      useJpms = nativeImageUseJpms(),
      workspace = BuildCtx.workspaceRoot,
      withFilesystemChecker = generateNativeImageWithFileSystemChecker,
    )

    val scriptName = if Properties.isWin then "generate.bat" else "generate.sh"
    val scriptPath = Task.dest / scriptName

    def bashScript = {
      val q                                                = "\'"
      def extra(from: os.Path, to: os.Path, move: Boolean) =
        System.lineSeparator() +
          s"mkdir -p $q${to / os.up}$q" +
          System.lineSeparator() +
          s"${if move then "mv" else "cp"} $q$from$q $q$to$q"

      val extra0 = tmpDestOpt.fold("") { tmpDest =>
        extra(tmpDest, actualDest, move = true)
      }

      val extra1 = imageDestOpt.fold("") { imageDest =>
        extra(actualDest, imageDest, move = false)
      }

      val envLines = extraEnv
        .toVector
        .map {
          case (k, v) =>
            val q = "\""
            s"export $k=$q${v.replace("\"", "\\\"")}$q" + System.lineSeparator()
        }
        .mkString

      s"""#!/usr/bin/env bash
         |set -e
         |$envLines${command.map(a => q + a.replace(q, "\\" + q) + q).mkString(" ")}
         |""".stripMargin + extra0 + extra1
    }

    def batScript = {
      val q                                                = "\""
      def extra(from: os.Path, to: os.Path, move: Boolean) =
        System.lineSeparator() +
          s"md $q${to / os.up}$q" +
          System.lineSeparator() +
          s"${if move then "mv" else "copy /y"} $q$from$q $q$to$q"

      val extra0 = tmpDestOpt.fold("") { tmpDest =>
        extra(tmpDest, actualDest, move = true)
      }

      val extra1 = imageDestOpt.fold("") { imageDest =>
        extra(actualDest, imageDest, move = false)
      }

      val envLines = extraEnv
        .toVector
        .map {
          case (k, v) =>
            val q = "\""
            s"set $q$k=${v.replace("\"", "\\\"")}$q" + System.lineSeparator()
        }
        .mkString

      s"""${envLines}@call ${command.map(a => q + a.replace(q, "\\" + q) + q).mkString(" ")}
         |""".stripMargin + extra0 + extra1
    }

    val content = if Properties.isWin then batScript else bashScript

    def writeScript(): Unit =
      os.write.over(scriptPath, content.getBytes(Charset.defaultCharset()), createFolders = true)

    if generateNativeImageWithFileSystemChecker then writeScript()
    else
      BuildCtx.withFilesystemCheckerDisabled {
        Task.log.info("nativeImageScript: skipping Mill file system checker and writing script")
        writeScript()
      }

    if !Properties.isWin then os.perms.set(scriptPath, "rwxr-xr-x")

    PathRef(scriptPath)
  }

  def writeNativeImageScript(scriptDest: String, imageDest: String = ""): Task.Command[Unit] =
    Task.Command {
      val scriptDest0 = os.Path(scriptDest, BuildCtx.workspaceRoot)
      val script      = nativeImageScript(imageDest)().path
      os.copy(script, scriptDest0, replaceExisting = true, createFolders = true)
    }

  def nativeImage: T[PathRef] =
    if nativeImagePersist then
      Task(persistent = true) {
        val cp         = nativeImageClassPath().map(_.path)
        val mainClass0 = nativeImageMainClass()
        val dest       = Task.dest / nativeImageName()
        val actualDest = Task.dest / (nativeImageName() + platformExtension)

        if os.isFile(actualDest) then
          Task.log.info(
            s"Warning: not re-computing ${actualDest.relativeTo(BuildCtx.workspaceRoot)}, delete it if you think it's stale"
          )
        else {
          val (command, tmpDestOpt, extraEnv) = generateNativeImage(
            graalVmHome = nativeImageGraalvmHome().path,
            jvmId = nativeImageGraalVmJvmId(),
            classPath = cp,
            mainClass = mainClass0,
            dest = dest,
            nativeImageOptions = nativeImageOptions(),
            dockerParamsOpt = nativeImageDockerParams(),
            dockerWorkingDir = nativeImageDockerWorkingDir(),
            createManifest = nativeImageUseManifest(),
            workingDir = Task.dest / "working-dir",
            useJpms = nativeImageUseJpms(),
            workspace = BuildCtx.workspaceRoot,
            withFilesystemChecker = generateNativeImageWithFileSystemChecker,
          )

          val res = os.proc(command.map(x => x: os.Shellable)*).call(
            stdin = os.Inherit,
            stdout = os.Inherit,
            env = extraEnv,
            cwd = BuildCtx.workspaceRoot,
          )
          if (res.exitCode == 0)
            for (tmpDest <- tmpDestOpt)
              if (generateNativeImageWithFileSystemChecker)
                os.copy(tmpDest, dest)
              else
                BuildCtx.withFilesystemCheckerDisabled {
                  os.copy(tmpDest, dest)
                }
          else
            sys.error(s"native-image command exited with ${res.exitCode}")
        }

        PathRef(actualDest)
      }
    else
      Task {
        val cp         = nativeImageClassPath().map(_.path)
        val mainClass0 = nativeImageMainClass()
        val dest       = Task.dest / nativeImageName()
        val actualDest = Task.dest / (nativeImageName() + platformExtension)

        val (command, tmpDestOpt, extraEnv) = generateNativeImage(
          graalVmHome = nativeImageGraalvmHome().path,
          jvmId = nativeImageGraalVmJvmId(),
          classPath = cp,
          mainClass = mainClass0,
          dest = dest,
          nativeImageOptions = nativeImageOptions(),
          dockerParamsOpt = nativeImageDockerParams(),
          dockerWorkingDir = nativeImageDockerWorkingDir(),
          createManifest = nativeImageUseManifest(),
          workingDir = Task.dest / "working-dir",
          useJpms = nativeImageUseJpms(),
          workspace = BuildCtx.workspaceRoot,
          withFilesystemChecker = generateNativeImageWithFileSystemChecker,
        )

        val res = os.proc(command.map(x => x: os.Shellable)*).call(
          stdin = os.Inherit,
          stdout = os.Inherit,
          env = extraEnv,
          cwd = BuildCtx.workspaceRoot,
        )
        if (res.exitCode == 0)
          for (tmpDest <- tmpDestOpt)
            if (generateNativeImageWithFileSystemChecker)
              os.copy(tmpDest, dest)
            else
              BuildCtx.withFilesystemCheckerDisabled {
                os.copy(tmpDest, dest)
              }
        else
          sys.error(s"native-image command exited with ${res.exitCode}")

        PathRef(actualDest)
      }

}

object NativeImage extends NativeImageCompat {
  def defaultGraalVmVersion: String = "22.3.0"

  def defaultLinuxStaticDockerImage: String =
    "messense/rust-musl-cross@sha256:12d0dd535ef7364bf49cb2608ae7eaf60e40d07834eb4d9160c592422a08d3b3"
  def csLinuxX86_64Url(version: String): String =
    s"https://github.com/coursier/coursier/releases/download/v$version/cs-x86_64-pc-linux"

  def linuxStaticParams(dockerImage: String, csUrl: String): DockerParams =
    DockerParams(
      imageName = dockerImage,
      prepareCommand =
        """(cd /usr/local/musl/bin && ln -s *-musl-gcc musl-gcc) && export PATH="/usr/local/musl/bin:$PATH"""",
      csUrl = csUrl,
      extraNativeImageArgs = Seq(
        "--static",
        "--libc=musl",
      ),
    )
  def linuxStaticParams(): DockerParams =
    linuxStaticParams(defaultLinuxStaticDockerImage, csLinuxX86_64Url("2.0.16"))

  def defaultLinuxMostlyStaticDockerImage:                         String       = "ubuntu:18.04"
  def linuxMostlyStaticParams(dockerImage: String, csUrl: String): DockerParams =
    DockerParams(
      imageName = dockerImage,
      prepareCommand = "apt-get update -q -y && apt-get install -q -y build-essential libz-dev",
      csUrl = csUrl,
      extraNativeImageArgs = Seq(
        "-H:+StaticExecutableWithDynamicLibC"
      ),
    )
  def linuxMostlyStaticParams(): DockerParams =
    linuxMostlyStaticParams(defaultLinuxMostlyStaticDockerImage, csLinuxX86_64Url("2.0.16"))

  @deprecated("Not used anymore by mill-native-image", "0.2.6")
  lazy val systemCs: String =
    if Properties.isWin then {
      val pathExt = Option(System.getenv("PATHEXT"))
        .toSeq
        .flatMap(_.split(File.pathSeparator).toSeq)
      val path = Option(System.getenv("PATH"))
        .toSeq
        .flatMap(_.split(File.pathSeparator))
        .map(new File(_))

      def candidates =
        for {
          dir <- path.iterator
          ext <- pathExt.iterator
        } yield new File(dir, s"cs$ext")

      candidates
        .filter(_.canExecute)
        .to(LazyList)
        .headOption
        .map(_.getAbsolutePath)
        .getOrElse {
          System.err.println("Warning: cs not found in PATH")
          "cs"
        }
    } else
      "cs"

  def platformExtension: String = if Properties.isWin then ".exe" else ""

  // should be the default index in the upcoming coursier release (> 2.0.16)
  def jvmIndex: String = "https://github.com/coursier/jvm-index/raw/master/index.json"

  private def vcVersions:    Seq[String]      = Seq("18", "2026", "2022", "2019", "2017")
  private def vcEditions:    Seq[String]      = Seq("Enterprise", "Community", "BuildTools")
  lazy val vcvarsCandidates: Iterable[String] = Option(System.getenv("VCVARSALL")) ++ {
    for {
      isX86   <- Seq(false, true)
      version <- vcVersions
      edition <- vcEditions
    } yield {
      val programFiles = if isX86 then "Program Files (x86)" else "Program Files"
      """C:\""" + programFiles + """\Microsoft Visual Studio\""" + version + "\\" + edition +
        """\VC\Auxiliary\Build\vcvars64.bat"""
    }
  }

  @deprecated("Use vcvarsOpt0 instead", "0.1.31")
  def vcvarsOpt: Option[os.Path] =
    vcvarsOpt0(os.pwd) // FIXME os.pwd isn't the workspace anymore with recent Mill versions

  def vcvarsOpt0(workspace: os.Path): Option[os.Path] =
    vcvarsCandidates
      .iterator
      .map(os.Path(_, workspace))
      .filter(os.exists(_))
      .to(LazyList)
      .headOption

  final case class DockerParams(
    imageName:            String,
    prepareCommand:       String,
    csUrl:                String,
    extraNativeImageArgs: Seq[String],
  )

  object DockerParams {
    implicit val codec: upickle.default.ReadWriter[DockerParams] = upickle.default.macroRW[DockerParams]
  }

  def generateNativeImage(
    graalVmHome:           os.Path,
    jvmId:                 String,
    classPath:             Seq[os.Path],
    mainClass:             String,
    dest:                  os.Path,
    nativeImageOptions:    Seq[String],
    dockerParamsOpt:       Option[DockerParams],
    dockerWorkingDir:      os.Path,
    createManifest:        Boolean,
    workingDir:            os.Path,
    useJpms:               Option[Boolean],
    workspace:             os.Path,
    withFilesystemChecker: Boolean,
  ): (Seq[String], Option[os.Path], Map[String, String]) = {

    val ext         = if Properties.isWin then ".cmd" else ""
    val nativeImage = graalVmHome / "bin" / s"native-image$ext"

    if !os.isFile(nativeImage) then {
      val ret = os.proc(graalVmHome / "bin" / s"gu$ext", "install", "native-image").call(
        stdin = os.Inherit,
        stdout = os.Inherit,
      )
      if ret.exitCode != 0 then
        System.err.println(s"Warning: 'gu install native-image' exited with return code ${ret.exitCode}}")
      if !os.isFile(nativeImage) then
        System.err.println(s"Warning: $nativeImage not found, and not installed by 'gu install native-image'")
    }

    val finalCp =
      if createManifest then {
        import java.util.jar.*
        val manifest   = new Manifest
        val attributes = manifest.getMainAttributes
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
        attributes.put(Attributes.Name.CLASS_PATH, classPath.map(absPath).mkString(" "))
        val jarFile = File.createTempFile("classpathJar", ".jar")
        val jos     = new JarOutputStream(new java.io.FileOutputStream(jarFile), manifest)
        jos.close()
        jarFile.getAbsolutePath
      } else
        classPath.map(absPath).mkString(File.pathSeparator)

    def command(
      nativeImage:          String,
      extraNativeImageArgs: Seq[String],
      destDir:              Option[String],
      destName:             String,
      classPath:            String,
    ) = {
      val destDirOptions = destDir.toList.map(d => s"-H:Path=$d")
      Seq(nativeImage) ++
        extraNativeImageArgs ++
        nativeImageOptions ++
        destDirOptions ++
        Seq(
          s"-H:Name=$destName",
          "-cp",
          classPath,
          mainClass,
        )
    }

    def defaultCommand: Seq[String] = {
      val absDest    = absNioPath(dest).normalize
      val destDirOpt = Option(absDest.getParent).map(_.toString)
      val destName   = absDest.getFileName.toString
      command(absPath(nativeImage), Nil, destDirOpt, destName, finalCp)
    }

    def default: (Seq[String], Option[os.Path], Map[String, String]) = {
      val extraEnv = useJpms match {
        case None    => Map.empty[String, String]
        case Some(v) => Map("USE_NATIVE_IMAGE_JAVA_PLATFORM_MODULE_SYSTEM" -> v.toString)
      }
      (defaultCommand, None, extraEnv)
    }

    val (finalCommand: Seq[String], tmpDestOpt: Option[os.Path], extraEnv: Map[String, String]) =
      if Properties.isWin then
        vcvarsOpt0(workspace) match {
          case None =>
            System.err.println(s"Warning: vcvarsall script not found in predefined locations:")
            for (loc <- vcvarsCandidates)
              System.err.println(s"  $loc")
            default
          case Some(vcvars) =>
            // chcp 437 sometimes needed, see https://github.com/oracle/graal/issues/2522
            val escapedCommand = defaultCommand.map {
              case s if s.contains(" ") => "\"" + s + "\""
              case s                    => s
            }
            val jpmsLine = useJpms match {
              case None    => ""
              case Some(v) => s"set USE_NATIVE_IMAGE_JAVA_PLATFORM_MODULE_SYSTEM=$v" + System.lineSeparator()
            }
            val script =
              s"""chcp 437
                 |@call "$vcvars"
                 |if %errorlevel% neq 0 exit /b %errorlevel%
                 |${jpmsLine}@call ${escapedCommand.mkString(" ")}
                 |""".stripMargin
            val f = () => {
              val scriptPath = workingDir / "run-native-image.bat"
              os.write.over(scriptPath, script.getBytes, createFolders = true)
              (Seq("cmd", "/c", absPath(scriptPath)), None, Map.empty[String, String])
            }
            if withFilesystemChecker then f()
            else
              BuildCtx.withFilesystemCheckerDisabled {
                System.err.println("generateNativeImage: skipping Mill file system checker")
                f()
              }
        }
      else
        dockerParamsOpt match {
          case Some(params) =>
            val f = () => {
              var entries = Set.empty[String]
              val cpDir   = dockerWorkingDir / "cp"
              if os.exists(cpDir) then os.remove.all(cpDir)
              os.makeDir.all(cpDir)
              val copiedCp = classPath.filter(os.exists(_)).map { f =>
                val name =
                  if entries(f.last) then {
                    var i           = 1
                    val (base, ext) =
                      if f.last.endsWith(".jar") then (f.last.stripSuffix(".jar"), ".jar") else (f.last, "")
                    var candidate = ""
                    while ({
                      candidate = s"$base-$i$ext"
                      entries(candidate)
                    }) {
                      i += 1
                    }
                    candidate
                  } else f.last
                entries = entries + name
                val dest = cpDir / name
                os.copy(f, dest)
                s"/data/cp/$name"
              }
              val cp             = copiedCp.mkString(File.pathSeparator)
              val escapedCommand =
                command("native-image", params.extraNativeImageArgs, Some("/data"), "output", cp).map {
                  case s if s.contains(" ") || s.contains("$") || s.contains("\"") || s.contains("'") =>
                    "'" + s.replace("'", "\\'") + "'"
                  case s => s
                }
              val jpmsLine = useJpms match {
                case None    => ""
                case Some(v) => s"export USE_NATIVE_IMAGE_JAVA_PLATFORM_MODULE_SYSTEM=$v" + System.lineSeparator()
              }
              val backTick = "\\"
              val script   =
                s"""#!/usr/bin/env bash
                   |set -e
                   |${params.prepareCommand}
                   |${jpmsLine}eval "$$(/data/cs java --env --jvm "$jvmId" --jvm-index "$jvmIndex")"
                   |native-image --help >/dev/null || gu install native-image
                   |${escapedCommand.head}""".stripMargin + escapedCommand.drop(1).map("\\\n  " + _).mkString + "\n"
              val scriptPath = dockerWorkingDir / "run-native-image.sh"
              os.write.over(scriptPath, script, createFolders = true)
              os.perms.set(scriptPath, "rwxr-xr-x")
              import coursier.cache.FileCache
              import coursier.util.{Artifact, Task => CsTask}
              val csFileCache = FileCache[CsTask]()
              val csPath      = os.Path(
                csFileCache.file(Artifact.fromUrl(params.csUrl)).run
                  .unsafeRun(wrapExceptions = true)(
                    using csFileCache.ec
                  )
                  .fold(e => throw e, _.getAbsolutePath)
              )
              if csPath.last.endsWith(".gz") then {
                os.copy.over(csPath, dockerWorkingDir / "cs.gz")
                os.proc("gzip", "-df", dockerWorkingDir / "cs.gz").call(stdout = os.Inherit)
              } else os.copy.over(csPath, dockerWorkingDir / "cs")
              os.perms.set(dockerWorkingDir / "cs", "rwxr-xr-x")
              val termOpt   = if System.console() == null then Nil else Seq("-t")
              val dockerCmd = Seq("docker", "run") ++ termOpt ++ Seq(
                "--rm",
                "-v",
                s"${absPath(dockerWorkingDir)}:/data",
                "-e",
                "COURSIER_JVM_CACHE=/data/jvm-cache",
                "-e",
                "COURSIER_CACHE=/data/cs-cache",
                params.imageName,
                "/data/run-native-image.sh",
              )
              (dockerCmd, Some(dockerWorkingDir / "output"), Map.empty[String, String])
            }
            if withFilesystemChecker then f()
            else
              BuildCtx.withFilesystemCheckerDisabled {
                System.err.println("generateNativeImage: skipping Mill file system checker")
                f()
              }
          case None =>
            default
        }

    (finalCommand, tmpDestOpt, extraEnv)
  }

}
