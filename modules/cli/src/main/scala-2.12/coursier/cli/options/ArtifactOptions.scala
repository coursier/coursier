package coursier.cli.options

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.core.{Classifier, Resolution, Type}

object ArtifactOptions {
  def defaultArtifactTypes = Resolution.defaultTypes

  implicit val parser = Parser[ArtifactOptions]
  implicit val help = caseapp.core.help.Help[ArtifactOptions]
}

final case class ArtifactOptions(
  @Help("Fetch source artifacts")
    sources: Boolean = false,
  @Help("Fetch javadoc artifacts")
    javadoc: Boolean = false,
  @Help("Fetch default artifacts (default: false if --sources or --javadoc or --classifier are passed, true else)")
    default: Option[Boolean] = None,
  @Help("Artifact types that should be retained (e.g. jar, src, doc, etc.) - defaults to jar,bundle")
  @Value("type1,type2,...")
  @Short("A")
    artifactType: List[String] = Nil,
  @Help("Fetch artifacts even if the resolution is errored")
    force: Boolean = false
) {

  def default0(classifiers: Set[Classifier]): Boolean =
    default.getOrElse {
      (!sources && !javadoc && classifiers.isEmpty) ||
        classifiers(Classifier("_"))
    }

  def artifactTypes(): Set[Type] =
    artifactTypes(Set())
  def artifactTypes(classifiers: Set[Classifier]): Set[Type] = {

    val types0 = artifactType
      .flatMap(_.split(','))
      .filter(_.nonEmpty)
      .map(Type(_))
      .toSet

    if (types0.isEmpty) {
      val sourceTypes = Some(Type.source).filter(_ => sources || classifiers(Classifier.sources)).toSet
      val javadocTypes = Some(Type.doc).filter(_ => javadoc || classifiers(Classifier.javadoc)).toSet
      val defaultTypes = if (default0(classifiers)) ArtifactOptions.defaultArtifactTypes else Set()
      sourceTypes ++ javadocTypes ++ defaultTypes
    } else if (types0(Type("*")))
      Set(Type("*"))
    else
      types0
  }
}
