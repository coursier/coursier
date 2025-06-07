import $file.cs
import $file.deps, deps.{Deps, Docker, graalVmJvmId}
import $file.modules.shared, shared.CsModule

import io.github.alexarchambault.millnativeimage.NativeImage
import mill._, mill.scalalib._

import java.io.File

import scala.util.Properties

def platformExtension: String =
  if (Properties.isWin) ".exe"
  else ""

def platformBootstrapExtension: String =
  if (Properties.isWin) ".bat"
  else ""

def platformSuffix: String = {
  val arch = sys.props("os.arch").toLowerCase(java.util.Locale.ROOT) match {
    case "amd64" => "x86_64"
    case "arm64" => "aarch64"
    case other   => other
  }
  val os = {
    val p = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT)
    if (p.contains("linux")) "pc-linux"
    else if (p.contains("mac")) "apple-darwin"
    else if (p.contains("windows")) "pc-win32"
    else sys.error(s"Unrecognized OS: $p")
  }

  s"$arch-$os"
}

trait Launchers extends CsModule {

  trait CliNativeImage extends NativeImage {

    def nativeImageClassPath = runClasspath()
    def nativeImageMainClass = mainClass().getOrElse(sys.error("No main class"))

    def nativeImageCsCommand    = Seq(cs.cs)
    def nativeImagePersist      = System.getenv("CI") != null
    def nativeImageGraalVmJvmId = graalVmJvmId

    def nativeImageName                                                     = "cs"
    private def staticLibDirName                                            = "native-libs"
    private def copyCsjniutilTo(destDir: os.Path, workspace: os.Path): Unit = {
      val jniUtilsVersion = Deps.jniUtils.dep.versionConstraint.asString
      val libRes          = os.proc(
        cs.cs,
        "fetch",
        "--intransitive",
        s"io.get-coursier.jniutils:windows-jni-utils:$jniUtilsVersion,classifier=x86_64-pc-win32,ext=lib,type=lib",
        "-A",
        "lib"
      ).call()
      val libPath = os.Path(libRes.out.text().trim(), workspace)
      os.copy.over(libPath, destDir / "csjniutils.lib")
    }

    def staticLibDir = T {
      val dir = nativeImageDockerWorkingDir() / staticLibDirName
      os.makeDir.all(dir)

      if (Properties.isWin)
        copyCsjniutilTo(dir, T.workspace)

      PathRef(dir)
    }

    def nativeImageOptions = T {
      val usesDocker = nativeImageDockerParams().nonEmpty
      val cLibPath   =
        if (usesDocker) s"/data/$staticLibDirName"
        else staticLibDir().path.toString
      val zstdOpt =
        if (Properties.isWin && (arch == "x86_64" || arch == "amd64"))
          Seq(s"-H:IncludeResources=win/amd64/libzstd-jni-.*\\.dll")
        else if (Properties.isMac && (arch == "x86_64" || arch == "amd64"))
          Seq(s"-H:IncludeResources=darwin/x86_64/libzstd-jni-.*\\.dylib")
        else if (Properties.isMac && arch == "aarch64")
          Seq(s"-H:IncludeResources=darwin/aarch64/libzstd-jni-.*\\.dylib")
        else if (Properties.isLinux && (arch == "x86_64" || arch == "amd64"))
          Seq(s"-H:IncludeResources=linux/amd64/libzstd-jni-.*\\.so")
        else if (Properties.isLinux && arch == "aarch64")
          Seq(s"-H:IncludeResources=linux/aarch64/libzstd-jni-.*\\.so")
        else {
          System.err.println("Warning: not sure which zstd-jni library to embed")
          Nil
        }
      val extraOpts =
        if (Properties.isLinux && arch == "aarch64")
          Seq(
            // required on the Linux / ARM64 CI in particular (not sure why)
            "-Djdk.lang.Process.launchMechanism=vfork", // https://mbien.dev/blog/entry/custom-java-runtimes-with-jlink
            "-H:PageSize=65536" // Make sure binary runs on kernels with page size set to 4k, 16 and 64k
          )
        else
          Nil
      Seq(
        s"-H:CLibraryPath=$cLibPath",
        "--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.core.jdk=ALL-UNNAMED",
        "--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.hosted=ALL-UNNAMED",
        "--add-exports=org.graalvm.nativeimage.builder/com.oracle.svm.hosted.c=ALL-UNNAMED"
      ) ++
        extraOpts ++
        zstdOpt
    }
  }

  object `base-image` extends CliNativeImage

  private def compatNativeImageOptions = Seq(
    "--native-compiler-options=-march=x86-64",
    "--native-compiler-options=-mtune=generic"
  )
  object `compat-image` extends CliNativeImage {
    def nativeImageOptions = super.nativeImageOptions() ++ compatNativeImageOptions
  }

  private val arch = sys.props.getOrElse("os.arch", "").toLowerCase(java.util.Locale.ROOT)
  private def isCI = System.getenv("CI") != null
  def nativeImage  =
    if (Properties.isLinux && isCI)
      `linux-docker-image`.nativeImage
    else
      `base-image`.nativeImage

  def compatNativeImage =
    if (Properties.isLinux && isCI)
      `linux-compat-docker-image`.nativeImage
    else
      `compat-image`.nativeImage

  object `linux-docker-image` extends CliNativeImage {
    def nativeImageDockerParams = Some(
      NativeImage.DockerParams(
        imageName = "ubuntu:20.04",
        prepareCommand =
          """apt-get update -q -y &&\
            |apt-get install -q -y build-essential libz-dev zlib1g-dev git python3-pip curl zip
            |export LANG=en_US.UTF-8
            |export LANGUAGE=en_US:en
            |export LC_ALL=en_US.UTF-8""".stripMargin,
        csUrl = linuxCsLauncher,
        extraNativeImageArgs = Nil
      )
    )
  }

  object `linux-compat-docker-image` extends CliNativeImage {
    def nativeImageDockerParams = Some(
      NativeImage.DockerParams(
        imageName = "ubuntu:20.04",
        prepareCommand =
          """apt-get update -q -y &&\
            |apt-get install -q -y build-essential libz-dev zlib1g-dev git python3-pip curl zip
            |export LANG=en_US.UTF-8
            |export LANGUAGE=en_US:en
            |export LC_ALL=en_US.UTF-8""".stripMargin,
        csUrl = linuxCsLauncher,
        extraNativeImageArgs = Nil
      )
    )
    def nativeImageOptions = super.nativeImageOptions() ++ compatNativeImageOptions
  }

  private def linuxCsLauncher =
    if (arch == "aarch64")
      s"https://github.com/VirtusLab/coursier-m1/releases/download/v${deps.csDockerVersion}/cs-aarch64-pc-linux.gz"
    else
      s"https://github.com/coursier/coursier/releases/download/v${deps.csDockerVersion}/cs-x86_64-pc-linux.gz"

  private def setupLocaleAndOptions(params: NativeImage.DockerParams): NativeImage.DockerParams =
    params.copy(
      prepareCommand = params.prepareCommand +
        """
          |set -v
          |apt-get update
          |apt-get install -q -y locales
          |export LANG=en_US.UTF-8
          |export LANGUAGE=en_US:en
          |export LC_ALL=en_US.UTF-8""".stripMargin
    )

  object `static-image` extends CliNativeImage {
    def nativeImageDockerParams = T {
      val baseDockerParams = NativeImage.linuxStaticParams(
        Docker.muslBuilder,
        linuxCsLauncher
      )
      val dockerParams = setupLocaleAndOptions(baseDockerParams)
      buildHelperImage()
      Some(dockerParams)
    }
    def buildHelperImage = T {
      val imageDirName = if (arch == "aarch64") "musl-image-arm64" else "musl-image"
      os.proc("docker", "build", "-t", Docker.customMuslBuilderImageName, ".")
        .call(cwd = T.workspace / "project" / imageDirName, stdout = os.Inherit)
      ()
    }
    def writeNativeImageScript(scriptDest: String, imageDest: String = "") = T.command {
      buildHelperImage()
      super.writeNativeImageScript(scriptDest, imageDest)()
    }
  }

  object `mostly-static-image` extends CliNativeImage {
    def nativeImageDockerParams = T {
      val baseDockerParams = NativeImage.linuxMostlyStaticParams(
        if (arch == "aarch64") "ubuntu:20.04" else "ubuntu:18.04", // TODO Pin that
        linuxCsLauncher
      )
      val dockerParams = setupLocaleAndOptions(baseDockerParams)
      Some(dockerParams)
    }
  }

  object `container-image` extends CliNativeImage {
    def nativeImageOptions = super.nativeImageOptions() ++ Seq(
      "-H:-UseContainerSupport"
    )
  }

  // Same as container-image, but built from docker to avoid glibc version issues
  object `container-image-from-docker` extends CliNativeImage {
    def nativeImageDockerParams = `linux-docker-image`.nativeImageDockerParams()
    def nativeImageOptions      = super.nativeImageOptions() ++ Seq(
      "-H:-UseContainerSupport"
    )
  }

  def containerImage =
    if (Properties.isLinux && isCI)
      `container-image-from-docker`.nativeImage
    else
      `container-image`.nativeImage

  def transitiveRunJars: T[Seq[PathRef]] = Task {
    T.traverse(transitiveModuleDeps)(_.jar)()
  }

  def runWithAssistedConfig(args: String*) = T.command {
    val cp          = jarClassPath().map(_.path).mkString(File.pathSeparator)
    val mainClass0  = mainClass().getOrElse(sys.error("No main class"))
    val graalVmHome = Option(System.getenv("GRAALVM_HOME")).getOrElse {
      import sys.process._
      Seq(
        cs.cs,
        "java-home",
        "--jvm",
        `base-image`.nativeImageGraalVmJvmId()
      ).!!.trim
    }
    val outputDir = T.dest / "config"
    val command   = Seq(
      s"$graalVmHome/bin/java",
      s"-agentlib:native-image-agent=config-output-dir=$outputDir",
      "-cp",
      cp,
      mainClass0
    ) ++ args
    os.proc(command.map(x => x: os.Shellable): _*).call(
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )
    T.log.outputStream.println(s"Config generated in ${outputDir.relativeTo(T.workspace)}")
  }

  def runFromJars(args: String*) = T.command {
    val cp         = jarClassPath().map(_.path).mkString(File.pathSeparator)
    val mainClass0 = mainClass().getOrElse(sys.error("No main class"))
    val command    = Seq("java", "-cp", cp, mainClass0) ++ args
    os.proc(command.map(x => x: os.Shellable): _*).call(
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )
  }

  def jarClassPath = T {
    val cp = runClasspath() ++ transitiveRunJars()
    cp.filter(ref => os.exists(ref.path) && !os.isDir(ref.path))
  }

  def launcher = T {
    import coursier.launcher.{
      AssemblyGenerator,
      BootstrapGenerator,
      ClassPathEntry,
      Parameters,
      Preamble
    }
    import scala.util.Properties.isWin
    val cp         = jarClassPath().map(_.path)
    val mainClass0 = mainClass().getOrElse(sys.error("No main class"))

    val dest = T.dest / (if (isWin) "launcher.bat" else "launcher")

    val preamble = Preamble()
      .withOsKind(isWin)
      .callsItself(isWin)
    val entries       = cp.map(path => ClassPathEntry.Url(path.toNIO.toUri.toASCIIString))
    val loaderContent = coursier.launcher.ClassLoaderContent(entries)
    val params        = Parameters.Bootstrap(Seq(loaderContent), mainClass0)
      .withDeterministic(true)
      .withPreamble(preamble)

    BootstrapGenerator.generate(params, dest.toNIO)

    PathRef(dest)
  }

  def standaloneLauncher = T {

    val cachePath = os.Path(coursier.cache.FileCache().location, T.workspace)
    def urlOf(path: os.Path): Option[String] =
      if (path.startsWith(cachePath)) {
        val segments = path.relativeTo(cachePath).segments
        val url      = segments.head + "://" + segments.tail.mkString("/")
        Some(url)
      }
      else None

    import coursier.launcher.{
      AssemblyGenerator,
      BootstrapGenerator,
      ClassPathEntry,
      Parameters,
      Preamble
    }
    import scala.util.Properties.isWin
    val cp         = jarClassPath().map(_.path)
    val mainClass0 = mainClass().getOrElse(sys.error("No main class"))

    val dest = T.dest / (if (isWin) "launcher.bat" else "launcher")

    val preamble = Preamble()
      .withOsKind(isWin)
      .callsItself(isWin)
    val entries = cp.map { path =>
      urlOf(path) match {
        case None =>
          val content = os.read.bytes(path)
          val name    = path.last
          ClassPathEntry.Resource(name, os.mtime(path), content)
        case Some(url) => ClassPathEntry.Url(url)
      }
    }
    val loaderContent = coursier.launcher.ClassLoaderContent(entries)
    val params        = Parameters.Bootstrap(Seq(loaderContent), mainClass0)
      .withDeterministic(true)
      .withPreamble(preamble)

    BootstrapGenerator.generate(params, dest.toNIO)

    PathRef(dest)
  }
}
