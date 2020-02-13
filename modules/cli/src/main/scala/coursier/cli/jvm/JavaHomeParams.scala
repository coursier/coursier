package coursier.cli.jvm

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.params.OutputParams
import coursier.params.CacheParams
import java.nio.file.Paths
import java.nio.file.Path

final case class JavaHomeParams(
  shared: SharedJavaParams,
  cache: CacheParams,
  output: OutputParams,
  setup: Boolean,
  homeOpt: Option[Path]
)

object JavaHomeParams {
  def apply(options: JavaHomeOptions): ValidatedNel[String, JavaHomeParams] = {
    val sharedV = SharedJavaParams(options.sharedJavaOptions)
    val cacheV = options.cacheOptions.params
    val outputV = OutputParams(options.outputOptions)
    val homeOpt = options.userHome.filter(_.nonEmpty).map(Paths.get(_))
    (sharedV, cacheV, outputV).mapN { (shared, cache, output) =>
      JavaHomeParams(
        shared,
        cache,
        output,
        options.setup,
        homeOpt
      )
    }
  }
}
