package coursierbuild.modules

import java.io.File
import com.github.lolgab.mill.mima.Mima
import coursierbuild.Deps.{Deps, ScalaVersions}

import mill._, mill.scalalib._, mill.scalajslib._

import java.util.Locale

import scala.util.Properties

trait CoursierJavaModule extends JavaModule {
  def jvmRelease = "8"
  private def csApp(workspace: os.Path): String =
    if (Properties.isWin) {
      def pathEntries = Option(System.getenv("PATH"))
        .iterator
        .flatMap(_.split(File.pathSeparator).iterator)
        .map(os.Path(_, workspace))
      val pathExts = Option(System.getenv("PATHEXT"))
        .iterator
        .flatMap(_.split(File.pathSeparator).iterator)
        .toSeq
      pathEntries
        .flatMap(dir => pathExts.iterator.map(ext => dir / s"cs$ext"))
        .find(os.isFile)
        .map(_.toString)
        .getOrElse {
          System.err.println("Warning: cannot find cs in PATH")
          "cs"
        }
    }
    else
      "cs"
  private def isArm64 =
    Option(System.getProperty("os.arch")).map(_.toLowerCase(Locale.ROOT)) match {
      case Some("aarch64" | "arm64") => true
      case _                         => false
    }
  def javacSystemJvmId = T {
    if (Properties.isMac && isArm64) s"zulu:$jvmRelease"
    else if (Properties.isWin && isArm64) s"liberica:$jvmRelease"
    else s"adoptium:$jvmRelease"
  }
  def javacSystemJvm = T.source {
    val output = os.proc(csApp(T.workspace), "java-home", "--jvm", javacSystemJvmId())
      .call(cwd = T.workspace)
      .out.trim()
    val javaHome = os.Path(output)
    assert(os.isDir(javaHome))
    PathRef(javaHome, quick = true)
  }
  // adds options equivalent to --release $jvmRelease + allowing access to unsupported JDK APIs
  // (no more straightforward options to achieve that AFAIK)
  def maybeJdkJavacOpt = T {
    val javaHome   = javacSystemJvm().path
    val rtJar      = javaHome / "jre/lib/rt.jar"
    val hasModules = os.isDir(javaHome / "jmods")
    val hasRtJar   = os.isFile(rtJar)
    assert(hasModules || hasRtJar)
    if (hasModules)
      Seq("--system", javaHome.toString)
    else
      Seq("-source", jvmRelease, "-target", jvmRelease, "-bootclasspath", rtJar.toString)
  }
  def javacOptions = T {
    super.javacOptions() ++ maybeJdkJavacOpt() ++ Seq(
      "-Xlint:unchecked"
    )
  }
}
