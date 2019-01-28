package coursier.cli.options.shared

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.core.{Classifier, Resolution, Type}

final case class ArtifactOptions(

  @Help("Classifiers that should be fetched")
  @Value("classifier1,classifier2,...")
  @Short("C")
    classifier: List[String] = Nil,

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
    forceFetch: Boolean = false

) {

  lazy val classifier0 = classifier.flatMap(_.split(',')).filter(_.nonEmpty).map(Classifier(_)).toSet

  def default0: Boolean =
    default.getOrElse {
      (!sources && !javadoc && classifier0.isEmpty) ||
        classifier0(Classifier("_"))
    }

  def artifactTypes: Set[Type] = {

    val types0 = artifactType
      .flatMap(_.split(',').toSeq)
      .filter(_.nonEmpty)
      .map(Type(_))
      .toSet

    if (types0.isEmpty) {
      val sourceTypes = Some(Type.source).filter(_ => sources || classifier0(Classifier.sources)).toSet
      val javadocTypes = Some(Type.doc).filter(_ => javadoc || classifier0(Classifier.javadoc)).toSet
      val defaultTypes = if (default0) Resolution.defaultTypes else Set()
      sourceTypes ++ javadocTypes ++ defaultTypes
    } else if (types0(Type.all))
      Set(Type.all)
    else
      types0
  }
}

object ArtifactOptions {
  implicit val parser = Parser[ArtifactOptions]
  implicit val help = caseapp.core.help.Help[ArtifactOptions]
}
