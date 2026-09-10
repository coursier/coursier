package coursier.cli.fetch

import java.nio.file.{Path, Paths}

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cli.install.SharedChannelParams
import coursier.cli.params.ArtifactParams
import coursier.cli.resolve.SharedResolveParams

final case class FetchParams(
  classpath: Boolean,
  jsonOutputOpt: Option[Path],
  jsonReportAddUrls: Boolean,
  resolve: SharedResolveParams,
  artifact: ArtifactParams,
  channel: SharedChannelParams,
  legacyReport: Boolean
)

object FetchParams {

  /** Marks the agent of a run that writes a JSON report.
    *
    * A report is read by a script or a CI job rather than by someone at a prompt, so it goes in the
    * comment - the product token stays `Coursier`, and the `cli` and `ci` tokens join it where they
    * apply. An agent passed explicitly wins over the lot.
    */
  private def withJsonReportUserAgent(resolve: SharedResolveParams): SharedResolveParams =
    resolve.copy(cache = resolve.cache.addUserAgentComments("json"))

  def apply(options: FetchOptions): ValidatedNel[String, FetchParams] = {

    val classpath = options.classpath

    val jsonOutputOpt =
      if (options.jsonOutputFile.isEmpty)
        None
      else
        Some(Paths.get(options.jsonOutputFile))

    val resolveV  = SharedResolveParams(options.resolveOptions)
    val artifactV = ArtifactParams(options.artifactOptions)
    val channelV  = SharedChannelParams(options.channelOptions)

    (resolveV, artifactV, channelV).mapN {
      (resolve, artifact, channel) =>
        FetchParams(
          classpath,
          jsonOutputOpt,
          options.jsonReportAddUrls,
          if (jsonOutputOpt.isEmpty) resolve else withJsonReportUserAgent(resolve),
          artifact,
          channel,
          options.legacyReportNoGuarantees.getOrElse(false)
        )
    }
  }
}
