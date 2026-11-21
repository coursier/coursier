package coursierbuild.modules

import com.github.lolgab.mill.mima.Mima
import coursier.cache.{ArchiveCache, CachePolicy, FileCache}
import coursier.jvm.{JavaHome, JvmCache}
import coursierbuild.{Deps, ScalaVersions}
import mill.*
import mill.api.*
import mill.scalalib.*
import mill.scalajslib.*

import java.util.Locale

import scala.util.Properties
import scala.util.control.NonFatal

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
    // The Windows x64 JDK zip of adoptium 8u512-b09 is broken on GitHub (it serves a 1.8 KB
    // Azure block list instead of the archive), and 8u514 has no Windows x64 zip yet
    else if (Properties.isWin && jvmRelease == "8") "adoptium:8.0-504"
    else s"adoptium:$jvmRelease"
  }
  def javacSystemJvm = Task {
    val javaHome = CoursierJavaModule.javaHome(javacSystemJvmId())
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

  private val javaHomeLock = new Object

  /** Gets a JVM, downloading and extracting it if needed
    *
    * GitHub sometimes serves broken JDK archives (a 1.8 KB Azure block list rather than the
    * archive, seen with adoptium 8u512 / 8u514), so this downloads it again a few times if it
    * cannot be extracted. Calls are serialized, so that the many modules needing the same JVM don't
    * download it concurrently.
    */
  def javaHome(id: String): os.Path = javaHomeLock.synchronized {
    def attempt(cache: FileCache[coursier.util.Task]): os.Path =
      JavaHome()
        .withCache(
          JvmCache()
            .copy(archiveCache = ArchiveCache().copy(cache = cache))
            .withDefaultIndex
        )
        .get(id)
        .map(os.Path(_))
        .unsafeRun(true)(using cache.ec)
    val maxAttempts = 4
    def loop(attemptNum: Int): os.Path = {
      // later attempts download the archive again, in case the cached one is broken
      val cache =
        if (attemptNum == 1) FileCache()
        else FileCache().copy(cachePolicies = Seq(CachePolicy.ForceDownload))
      try attempt(cache)
      catch {
        case NonFatal(e) if attemptNum < maxAttempts =>
          System.err.println(
            s"Getting JVM $id failed (attempt $attemptNum / $maxAttempts), trying again: $e"
          )
          Thread.sleep(2000L * attemptNum)
          loop(attemptNum + 1)
      }
    }
    loop(1)
  }

  def defaultJvmRelease = if (Properties.isWin && isArm64) "11" else "8"
  private def isArm64 =
    Option(System.getProperty("os.arch")).map(_.toLowerCase(Locale.ROOT)) match {
      case Some("aarch64" | "arm64") => true
      case _                         => false
    }
}
