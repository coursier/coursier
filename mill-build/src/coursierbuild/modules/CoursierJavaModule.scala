package coursierbuild.modules

import com.github.lolgab.mill.mima.Mima
import coursier.cache.ArchiveCache
import coursier.jvm.{JavaHome, JvmCache}
import coursierbuild.{Deps, ScalaVersions}
import mill.*
import mill.api.*
import mill.scalalib.*
import mill.scalajslib.*

import java.util.Locale

import scala.util.Properties

trait CoursierJavaModule extends JavaModule {
  def jvmRelease: String =
    CoursierJavaModule.defaultJvmRelease

  /** Java version of the class files this module compiles to, at least `jvmRelease` */
  def classFileJvmRelease: T[String] = Task {
    jvmRelease
  }
  def javacSystemJvmId = Task {
    if (Properties.isMac && CoursierJavaModule.isArm64) s"zulu:$jvmRelease"
    else if (Properties.isWin && CoursierJavaModule.isArm64) s"liberica:$jvmRelease"
    else s"adoptium:$jvmRelease"
  }
  def javacSystemJvm = Task {
    val cache = coursier.cache.Cache.default
    val javaHome = JavaHome()
      .withCache(
        JvmCache()
          .copy(archiveCache = ArchiveCache().copy(cache = cache))
          .withDefaultIndex
      )
      .get(javacSystemJvmId())
      .map(os.Path(_))
      .unsafeRun(true)(using cache.ec)
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
    // These options are cached, so they have to be real absolute paths. `toAbsString` alone
    // keeps the ephemeral out/mill-no-daemon/<id>/mill-home forwarder, that mill wipes when it
    // exits, in the path. `toResolvedPathString` follows it.
    val extraOpts =
      if (hasModules) Seq("--system", javaHome.toString)
      else Seq("-bootclasspath", rtJar.toString)
    // The Java 8 class files are on purpose: don't have javac warn that it's an obsolete target
    // in every module
    val lintOpts = if (jvmRelease == "8") Seq("-Xlint:-options") else Nil
    Seq("-source", jvmRelease, "-target", jvmRelease) ++ extraOpts ++ lintOpts
  }
  def javacOptions = Task {
    super.javacOptions() ++ maybeJdkJavacOpt() ++ Seq(
      "-Xlint:unchecked"
    )
  }
}

object CoursierJavaModule {
  def defaultJvmRelease = if (Properties.isWin && isArm64) "11" else "8"
  private def isArm64 =
    Option(System.getProperty("os.arch")).map(_.toLowerCase(Locale.ROOT)) match {
      case Some("aarch64" | "arm64") => true
      case _                         => false
    }
}
