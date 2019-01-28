package coursier.cli.params.shared

import cats.data.{Validated, ValidatedNel}
import coursier.cli.options.shared.ArtifactOptions
import coursier.core.{Classifier, Type}

final case class ArtifactParams(
  classifiers: Set[Classifier],
  mainArtifacts: Boolean,
  artifactTypes: Set[Type],
  force: Boolean
)

object ArtifactParams {
  def apply(options: ArtifactOptions): ValidatedNel[String, ArtifactParams] = {

    // TODO Move the logic of ArtifactOptions.classifier0 and all here
    val params = ArtifactParams(
      options.classifier0 ++
        (if (options.sources) Seq(Classifier.sources) else Nil) ++
        (if (options.javadoc) Seq(Classifier.javadoc) else Nil),
      options.default0,
      options.artifactTypes,
      options.forceFetch
    )

    Validated.validNel(params)
  }
}
