package coursierbuild.modules

import com.github.lolgab.mill.mima.Mima
import coursier.cache.{ArchiveCache, FileCache}
import coursier.util.Artifact
import coursierbuild.Deps.{Deps, ScalaVersions}
import mill.*
import mill.api.*
import mill.scalajslib.*
import mill.scalalib.*

import java.io.File
import java.util.Locale

import scala.util.Properties

trait CoursierJavaModule extends JavaModule {
  def jvmRelease = "8"
  private def csApp(workspace: os.Path): os.Shellable = {
    val csVersion = "2.1.25-M24"
    val (url, commandPrefix) =
      if (Properties.isLinux)
        if (isArm64)
          (
            s"https://github.com/coursier/coursier/releases/download/v$csVersion/cs-aarch64-pc-linux.gz!",
            Nil
          )
        else
          (
            s"https://github.com/coursier/coursier/releases/download/v$csVersion/cs-x86_64-pc-linux.gz!",
            Nil
          )
      else if (Properties.isMac)
        if (isArm64)
          (
            s"https://github.com/coursier/coursier/releases/download/v$csVersion/cs-aarch64-apple-darwin.gz!",
            Nil
          )
        else
          (
            s"https://github.com/coursier/coursier/releases/download/v$csVersion/cs-x86_64-apple-darwin.gz!",
            Nil
          )
      else if (Properties.isWin && !isArm64)
        (
          s"https://github.com/coursier/coursier/releases/download/v$csVersion/cs-x86_64-pc-win32.zip!cs-x86_64-pc-win32.exe",
          Nil
        )
      else
        (
          s"https://github.com/coursier/coursier/releases/download/v$csVersion/coursier",
          Seq("java", "-jar")
        )
    val cache        = FileCache()
    val archiveCache = ArchiveCache().withCache(cache)
    val f = archiveCache.get(Artifact(url)).unsafeRun(true)(using cache.ec)
      .left.map(e => throw e)
      .map(os.Path(_))
      .merge

    if (!Properties.isWin && !f.toIO.canExecute())
      f.toIO.setExecutable(true)

    (commandPrefix, f)
  }
  private def isArm64 =
    Option(System.getProperty("os.arch")).map(_.toLowerCase(Locale.ROOT)) match {
      case Some("aarch64" | "arm64") => true
      case _                         => false
    }
  def javacSystemJvmId = Task {
    if (Properties.isMac && isArm64) s"zulu:$jvmRelease"
    else if (Properties.isWin && isArm64) s"liberica:$jvmRelease"
    else s"adoptium:$jvmRelease"
  }
  def javacSystemJvm = Task {
    val output = os.proc(csApp(BuildCtx.workspaceRoot), "java-home", "--jvm", javacSystemJvmId())
      .call(cwd = BuildCtx.workspaceRoot)
      .out.trim()
    val javaHome = os.Path(output)
    assert(os.isDir(javaHome))
    PathRef(javaHome, quick = true)
  }
  // adds options equivalent to --release $jvmRelease + allowing access to unsupported JDK APIs
  // (no more straightforward options to achieve that AFAIK)
  def maybeJdkJavacOpt = Task {
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
  def javacOptions = Task {
    super.javacOptions() ++ maybeJdkJavacOpt() ++ Seq(
      "-Xlint:unchecked"
    )
  }
}
