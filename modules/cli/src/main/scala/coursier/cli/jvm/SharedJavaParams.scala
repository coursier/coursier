package coursier.cli.jvm

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

import cats.data.ValidatedNel
import cats.implicits._
import coursier.jvm.{JvmCache, JvmCacheLogger}
import cats.data.Validated

final case class SharedJavaParams(
  jvm: Option[String],
  jvmDir: Path
) {
  def id: String =
    jvm.getOrElse(coursier.jvm.JavaHome.defaultId)

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
    Validated.validNel {
      SharedJavaParams(
        jvm,
        jvmDir
      )
    }
  }
}
