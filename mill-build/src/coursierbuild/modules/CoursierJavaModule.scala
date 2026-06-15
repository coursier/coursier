package coursierbuild.modules

import com.github.lolgab.mill.mima.Mima
import coursier.cache.ArchiveCache
import coursier.jvm.{JavaHome, JvmCache}
import coursierbuild.Deps.{Deps, ScalaVersions}

import mill.*
import mill.api.*
import mill.scalalib.*
import mill.scalajslib.*

import java.util.Locale

import scala.util.Properties

trait CoursierJavaModule extends JavaModule {
  def jvmRelease: String =
    CoursierJavaModule.defaultJvmRelease
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
          .withArchiveCache(ArchiveCache().withCache(cache))
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
    val extraOpts =
      if (hasModules) Seq("--system", javaHome.toString)
      else Seq("-bootclasspath", rtJar.toString)
    Seq("-source", jvmRelease, "-target", jvmRelease) ++ extraOpts
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
