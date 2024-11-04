package coursier.cli.options

import caseapp._
import coursier.core.{Classifier, Resolution, Type}
import coursier.install.RawAppDescriptor

// format: off
final case class ArtifactOptions(

  @Group(OptionGroup.fetch)
  @Hidden
  @HelpMessage("Classifiers that should be fetched")
  @ValueDescription("classifier1,classifier2,...")
  @ExtraName("C")
    classifier: List[String] = Nil,

  @Group(OptionGroup.fetch)
  @HelpMessage("Fetch source artifacts")
    sources: Boolean = false,

  @Group(OptionGroup.fetch)
  @HelpMessage("Fetch javadoc artifacts")
    javadoc: Boolean = false,

  @Group(OptionGroup.fetch)
  @HelpMessage("Fetch default artifacts (default: false if --sources or --javadoc or --classifier are passed, true else)")
    default: Option[Boolean] = None,

  @Group(OptionGroup.fetch)
  @Hidden
  @HelpMessage("Artifact types that should be retained (e.g. jar, src, doc, etc.) - defaults to jar,bundle")
  @ValueDescription("type1,type2,...")
  @ExtraName("A")
    artifactType: List[String] = Nil,

  @Group(OptionGroup.fetch)
  @Hidden
  @HelpMessage("Fetch artifacts even if the resolution is errored")
    forceFetch: Boolean = false

) {
  // format: on

  // to deprecate
  lazy val classifier0 =
    classifier.flatMap(_.split(',')).filter(_.nonEmpty).map(Classifier(_)).toSet

  // to deprecate
  def default0: Boolean =
    default.getOrElse {
      (!sources && !javadoc && classifier0.isEmpty) ||
      classifier0(Classifier("_"))
    }

  // deprecated
  def artifactTypes: Set[Type] = {

    val types0 = artifactType
      .flatMap(_.split(',').toSeq)
      .filter(_.nonEmpty)
      .map(Type(_))
      .toSet

    if (types0.isEmpty) {
      val sourceTypes =
        Some(Type.source).filter(_ => sources || classifier0(Classifier.sources)).toSet
      val javadocTypes =
        Some(Type.doc).filter(_ => javadoc || classifier0(Classifier.javadoc)).toSet
      val defaultTypes = if (default0) Resolution.defaultTypes else Set()
      sourceTypes ++ javadocTypes ++ defaultTypes
    }
    else if (types0(Type.all))
      Set(Type.all)
    else
      types0
  }

  def addApp(app: RawAppDescriptor): ArtifactOptions =
    copy(
      classifier = {
        val previous = classifier
        previous ++ app.classifiers.filterNot(previous.toSet + "_")
      },
      default = default.orElse {
        if (app.classifiers.contains("_"))
          Some(true)
        else
          None
      },
      artifactType = {
        val previous = artifactType
        previous ++ app.artifactTypes.filterNot(previous.toSet)
      }
    )
}

object ArtifactOptions {
  lazy val parser: Parser[ArtifactOptions]                           = Parser.derive
  implicit lazy val parserAux: Parser.Aux[ArtifactOptions, parser.D] = parser
  implicit lazy val help: Help[ArtifactOptions]                      = Help.derive
}
