package coursier.cli.params

import cats.data.{Validated, ValidatedNel}
import coursier.cli.options.ArtifactOptions
import coursier.core.{Classifier, Type, VariantSelector}

final case class ArtifactParams(
  classifiers: Set[Classifier],
  attributes: Seq[VariantSelector.AttributesBased],
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
      if (options.sources) Seq(VariantSelector.AttributesBased.sources) else Nil,
      options.default0,
      options.artifactTypes,
      options.forceFetch
    )

    Validated.validNel(params)
  }
}
