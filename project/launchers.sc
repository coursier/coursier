import $ivy.`io.get-coursier::coursier-launcher:2.0.16`
import $ivy.`io.github.alexarchambault.mill::mill-native-image::0.1.21`

import $file.cs
import $file.deps, deps.{Deps, graalVmJvmId, jvmIndex}
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

trait Launchers extends CsModule with NativeImage {

  def transitiveJars: T[Agg[PathRef]] = {

    def allModuleDeps(todo: List[JavaModule]): List[JavaModule] =
      todo match {
        case Nil => Nil
        case h :: t =>
          h :: allModuleDeps(h.moduleDeps.toList ::: t)
      }

    T {
      mill.define.Target.traverse(allModuleDeps(this :: Nil).distinct)(m =>
        T.task(m.jar())
      )()
    }
  }

  def nativeImageCsCommand    = Seq(cs.cs)
  def nativeImagePersist      = System.getenv("CI") != null
  def nativeImageGraalVmJvmId = graalVmJvmId

  def nativeImageClassPath     = runClasspath()
  def nativeImageName          = "cs"
  def nativeImageMainClass     = mainClass().getOrElse(sys.error("No main class"))
  private def staticLibDirName = "native-libs"
  private def copyCsjniutilTo(destDir: os.Path): Unit = {
    val jniUtilsVersion = Deps.jniUtils.dep.version
    val libRes = os.proc(
      cs.cs,
      "fetch",
      "--intransitive",
      s"io.get-coursier.jniutils:windows-jni-utils:$jniUtilsVersion,classifier=x86_64-pc-win32,ext=lib,type=lib",
      "-A",
      "lib"
    ).call()
    val libPath = os.Path(libRes.out.text.trim, os.pwd)
    os.copy.over(libPath, destDir / "csjniutils.lib")
  }

  def staticLibDir = T {
    val dir = nativeImageDockerWorkingDir() / staticLibDirName
    os.makeDir.all(dir)

    if (Properties.isWin)
      copyCsjniutilTo(dir)

    PathRef(dir)
  }

  def nativeImageOptions = T {
    val usesDocker = nativeImageDockerParams().nonEmpty
    val cLibPath =
      if (usesDocker) s"/data/$staticLibDirName"
      else staticLibDir().path.toString
    val extraOpts =
      if (Properties.isLinux)
        Seq(
          // required on the Linux / ARM64 CI in particular (not sure why)
          "-Djdk.lang.Process.launchMechanism=vfork" // https://mbien.dev/blog/entry/custom-java-runtimes-with-jlink
        )
      else
        Nil
    Seq(s"-H:CLibraryPath=$cLibPath") ++
      extraOpts
  }

  def runWithAssistedConfig(args: String*) = T.command {
    val cp         = jarClassPath().map(_.path).mkString(File.pathSeparator)
    val mainClass0 = mainClass().getOrElse(sys.error("No main class"))
    val graalVmHome = Option(System.getenv("GRAALVM_HOME")).getOrElse {
      import sys.process._
      Seq(
        cs.cs,
        "java-home",
        "--jvm",
        nativeImageGraalVmJvmId(),
        "--jvm-index",
        jvmIndex
      ).!!.trim
    }
    val outputDir = T.ctx().dest / "config"
    val command = Seq(
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
    T.log.outputStream.println(s"Config generated in ${outputDir.relativeTo(os.pwd)}")
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
    val cp = runClasspath() ++ transitiveJars()
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

    val dest = T.ctx().dest / (if (isWin) "launcher.bat" else "launcher")

    val preamble = Preamble()
      .withOsKind(isWin)
      .callsItself(isWin)
    val entries       = cp.map(path => ClassPathEntry.Url(path.toNIO.toUri.toASCIIString))
    val loaderContent = coursier.launcher.ClassLoaderContent(entries)
    val params = Parameters.Bootstrap(Seq(loaderContent), mainClass0)
      .withDeterministic(true)
      .withPreamble(preamble)

    BootstrapGenerator.generate(params, dest.toNIO)

    PathRef(dest)
  }

  def standaloneLauncher = T {

    val cachePath = os.Path(coursier.cache.FileCache().location, os.pwd)
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

    val dest = T.ctx().dest / (if (isWin) "launcher.bat" else "launcher")

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
    val params = Parameters.Bootstrap(Seq(loaderContent), mainClass0)
      .withDeterministic(true)
      .withPreamble(preamble)

    BootstrapGenerator.generate(params, dest.toNIO)

    PathRef(dest)
  }
}
