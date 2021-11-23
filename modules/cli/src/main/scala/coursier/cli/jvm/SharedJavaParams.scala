package coursier.cli.jvm

import java.io.File
import java.nio.file.{Path, Paths}

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.Repository
import coursier.cache.{ArchiveCache, Cache}
import coursier.jvm.{JvmCache, JvmCacheLogger, JvmChannel, JvmIndex}
import coursier.util.Task

final case class SharedJavaParams(
  jvm: Option[String],
  allowSystemJvm: Boolean,
  requireSystemJvm: Boolean,
  update: Boolean,
  jvmChannelOpt: Option[JvmChannel]
) {
  def id: String =
    jvm.getOrElse(coursier.jvm.JavaHome.defaultId)

  def cacheAndHome(
    cache: Cache[Task],
    noUpdateCache: Cache[Task],
    repositories: Seq[Repository],
    verbosity: Int
  ): (JvmCache, coursier.jvm.JavaHome) = {
    def jvmCacheOf(cache: Cache[Task]) = {
      val archiveCache = ArchiveCache().withCache(cache)
      val c = JvmCache()
        .withArchiveCache(archiveCache)
      jvmChannelOpt match {
        case None             => c.withDefaultIndex
        case Some(jvmChannel) => c.withIndexChannel(repositories, jvmChannel)
      }
    }
    val noUpdateJvmCache = jvmCacheOf(noUpdateCache)
    val jvmCache         = jvmCacheOf(cache)
    val javaHome = coursier.jvm.JavaHome()
      .withCache(jvmCache)
      .withNoUpdateCache(Some(noUpdateJvmCache))
      .withAllowSystem(allowSystemJvm)
      .withUpdate(update)
    (jvmCache, javaHome)
  }
  def javaHome(
    cache: Cache[Task],
    noUpdateCache: Cache[Task],
    repositories: Seq[Repository],
    verbosity: Int
  ): coursier.jvm.JavaHome = {
    val (_, home) = cacheAndHome(
      cache,
      noUpdateCache,
      repositories,
      verbosity
    )
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
    val (allowSystem, requireSystem) = options.systemJvm match {
      case None        => (true, false)
      case Some(false) => (false, false)
      case Some(true)  => (true, true)
    }

    val checkSystemV =
      if (options.systemJvm.contains(true) && jvm.exists(_ != coursier.jvm.JavaHome.systemId))
        Validated.invalidNel("Cannot specify both --system-jvm and --jvm")
      else
        Validated.validNel(())

    val indexChannelOptV = {
      val parsed = options
        .jvmIndex
        .map(_.trim)
        .filter(_ != "default")
        .map(JvmIndex.handleAliases)
        .map(s => JvmChannel.parse(s))
      parsed match {
        case None                 => Validated.validNel(None)
        case Some(Left(err))      => Validated.invalidNel(s"Invalid --jvm-index value: $err")
        case Some(Right(channel)) => Validated.validNel(Some(channel))
      }
    }

    (checkSystemV, indexChannelOptV).mapN {
      (_, indexChannelOpt) =>
        SharedJavaParams(
          jvm,
          allowSystem,
          requireSystem,
          options.update,
          indexChannelOpt
        )
    }
  }
}
