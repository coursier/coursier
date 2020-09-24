package coursier.cli.jvm

import java.io.File
import java.nio.file.{Path, Paths}

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cache.Cache
import coursier.jvm.{JvmCache, JvmCacheLogger, JvmIndex}
import coursier.util.Task

final case class SharedJavaParams(
  jvm: Option[String],
  jvmDir: Path,
  allowSystemJvm: Boolean,
  requireSystemJvm: Boolean,
  localOnly: Boolean,
  update: Boolean,
  jvmIndexUrlOpt: Option[String]
) {
  def id: String =
    jvm.getOrElse(coursier.jvm.JavaHome.defaultId)

  def cacheAndHome(cache: Cache[Task], noUpdateCache: Cache[Task], verbosity: Int): (JvmCache, coursier.jvm.JavaHome) = {
    val noUpdateJvmCache = {
      val c = JvmCache()
        .withBaseDirectory(jvmDir.toFile)
        .withCache(noUpdateCache)
      jvmIndexUrlOpt match {
        case None => c.withDefaultIndex
        case Some(jvmIndexUrl) => c.withIndex(jvmIndexUrl)
      }
    }
    val jvmCache = {
      val c = JvmCache()
        .withBaseDirectory(jvmDir.toFile)
        .withCache(cache)
      jvmIndexUrlOpt match {
        case None => c.withDefaultIndex
        case Some(jvmIndexUrl) => c.withIndex(jvmIndexUrl)
      }
    }
    val javaHome = coursier.jvm.JavaHome()
      .withCache(jvmCache)
      .withNoUpdateCache(Some(noUpdateJvmCache))
      .withJvmCacheLogger(jvmCacheLogger(verbosity))
      .withAllowSystem(allowSystemJvm)
      .withInstallIfNeeded(!localOnly)
      .withUpdate(update)
    (jvmCache, javaHome)
  }
  def javaHome(cache: Cache[Task], noUpdateCache: Cache[Task], verbosity: Int): coursier.jvm.JavaHome = {
    val (_, home) = cacheAndHome(cache, noUpdateCache, verbosity)
    home
  }

  def jvmCacheLogger(verbosity: Int): JvmCacheLogger =
    if (verbosity >= 0)
      new JvmCacheLogger {
        def extracting(id: String, origin: String, dest: File): Unit =
          System.err.println(
            s"""Extracting
               |  $origin
               |in
               |  $dest""".stripMargin
          )
        def extracted(id: String, origin: String, dest: File): Unit =
          System.err.println("Done")
        def extractionFailed(id: String, origin: String, dest: File, error: Throwable): Unit =
          System.err.println(s"Extraction failed: $error")
      }
    else
      JvmCacheLogger.nop
}

object SharedJavaParams {
  def apply(options: SharedJavaOptions): ValidatedNel[String, SharedJavaParams] = {
    val jvm = options.jvm.map(_.trim).filter(_.nonEmpty)
    val jvmDir = options.jvmDir.filter(_.nonEmpty).map(Paths.get(_)).getOrElse {
      JvmCache.defaultBaseDirectory.toPath
    }
    val (allowSystem, requireSystem) = options.systemJvm match {
      case None => (true, false)
      case Some(false) => (false, false)
      case Some(true) => (true, true)
    }

    val checkSystemV =
      if (options.systemJvm.contains(true) && jvm.exists(_ != coursier.jvm.JavaHome.systemId))
        Validated.invalidNel("Cannot specify both --system-jvm and --jvm")
      else
        Validated.validNel(())

    val index = options
      .jvmIndex
      .map(_.trim)
      .filter(_ != "default")
      .map(JvmIndex.handleAliases)

    checkSystemV.map { _ =>
      SharedJavaParams(
        jvm,
        jvmDir,
        allowSystem,
        requireSystem,
        options.localOnly,
        options.update,
        index
      )
    }
  }
}
