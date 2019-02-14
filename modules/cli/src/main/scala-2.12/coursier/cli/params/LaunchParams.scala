package coursier.cli.params

import java.nio.file.{Path, Paths}

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.options.LaunchOptions
import coursier.cli.params.shared.{ArtifactParams, SharedLoaderParams}

final case class LaunchParams(
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

object LaunchParams {
  def apply(options: LaunchOptions): ValidatedNel[String, LaunchParams] = {

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
        LaunchParams(
          resolve,
          artifact,
          sharedLoader,
          mainClassOpt,
          extraJars
        )
    }
  }
}
