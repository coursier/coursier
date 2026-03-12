package coursier.cli.docker

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.params.{CacheParams, OutputParams}
import coursier.docker.DockerPull

final case class SharedDockerPullParams(
  cache: CacheParams,
  output: OutputParams,
  authRegistry: String,
  os: String,
  cpu: String,
  cpuVariant: Option[String]
)

object SharedDockerPullParams {
  def apply(options: SharedDockerPullOptions): ValidatedNel[String, SharedDockerPullParams] = {

    val authRegistry = options.authRegistry.map(_.trim).filter(_.nonEmpty).getOrElse(
      DockerPull.defaultAuthRegistry
    )

    (options.cacheOptions.params, OutputParams(options.outputOptions)).mapN {
      (cacheParams, outputParams) =>
        SharedDockerPullParams(
          cacheParams,
          outputParams,
          authRegistry,
          options.os.map(_.trim).filter(_.nonEmpty).getOrElse(DockerPull.defaultOs),
          options.cpu.map(_.trim).filter(_.nonEmpty).getOrElse(DockerPull.defaultArch),
          options.cpuVariant.map(_.trim).filter(_.nonEmpty).orElse(DockerPull.defaultArchVariant)
        )
    }
  }
}
