package coursier.cli.params.shared

import java.nio.file.{Path, Paths}

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.fetch.FetchParams
import coursier.cli.options.shared.SharedLaunchOptions
import coursier.cli.resolve.ResolveParams

final case class SharedLaunchParams(
  resolve: ResolveParams,
  artifact: ArtifactParams,
  sharedLoader: SharedLoaderParams,
  mainClassOpt: Option[String],
  extraJars: Seq[Path]
) {
  def fetch: FetchParams =
    FetchParams(
      classpath = false,
      jsonOutputOpt = None,
      resolve = resolve,
      artifact = artifact
    )
}

object SharedLaunchParams {
  def apply(options: SharedLaunchOptions): ValidatedNel[String, SharedLaunchParams] = {

    val resolveV = ResolveParams(options.resolveOptions)
    val artifactV = ArtifactParams(options.artifactOptions)
    val sharedLoaderV = resolveV.map(r => (r.dependency, r.resolution)).toOption match {
      case None =>
        Validated.validNel(SharedLoaderParams(Nil, Map.empty))
      case Some((depsOpts, resolutionOpts)) =>
        SharedLoaderParams(options.sharedLoaderOptions, resolutionOpts.selectedScalaVersion, depsOpts.defaultConfiguration)
    }

    val mainClassOpt = Some(options.mainClass).filter(_.nonEmpty)

    // check if those exist?
    val extraJars = options.extraJars.map { p =>
      Paths.get(p)
    }

    (resolveV, artifactV, sharedLoaderV).mapN {
      (resolve, artifact, sharedLoader) =>
        SharedLaunchParams(
          resolve,
          artifact,
          sharedLoader,
          mainClassOpt,
          extraJars
        )
    }
  }
}
