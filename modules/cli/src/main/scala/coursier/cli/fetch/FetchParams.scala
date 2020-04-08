package coursier.cli.fetch

import java.nio.file.{Path, Paths}

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.params.ArtifactParams
import coursier.cli.resolve.SharedResolveParams

final case class FetchParams(
  classpath: Boolean,
  jsonOutputOpt: Option[Path],
  resolve: SharedResolveParams,
  artifact: ArtifactParams
)

object FetchParams {
  def apply(options: FetchOptions): ValidatedNel[String, FetchParams] = {

    val classpath = options.classpath

    val jsonOutputOpt =
      if (options.jsonOutputFile.isEmpty)
        None
      else
        Some(Paths.get(options.jsonOutputFile))

    val resolveV = SharedResolveParams(options.resolveOptions)
    val artifactV = ArtifactParams(options.artifactOptions)

    (resolveV, artifactV).mapN {
      (resolve, artifact) =>
        FetchParams(
          classpath,
          jsonOutputOpt,
          resolve,
          artifact
        )
    }
  }
}
