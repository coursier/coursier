package coursier.cli.jvm

import java.io.File

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.params.OutputParams
import coursier.params.CacheParams
import coursier.jvm.JvmCacheLogger

final case class SharedJavaParams(
  jvm: Option[String],
  cache: CacheParams,
  output: OutputParams
) {
  def id: String =
    jvm.getOrElse(coursier.jvm.JavaHome.defaultId)

  def jvmCacheLogger(): JvmCacheLogger =
    if (output.verbosity >= 0)
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
    val cacheV = options.cacheOptions.params
    val outputV = OutputParams(options.outputOptions)
    (cacheV, outputV).mapN { (cache, output) =>
      SharedJavaParams(
        jvm,
        cache,
        output
      )
    }
  }
}
