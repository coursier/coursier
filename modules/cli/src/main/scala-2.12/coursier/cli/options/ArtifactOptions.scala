package coursier.cli.options

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.core.{Resolution, Type}

object ArtifactOptions {
  def defaultArtifactTypes = Resolution.defaultTypes

  implicit val parser = Parser[ArtifactOptions]
  implicit val help = caseapp.core.help.Help[ArtifactOptions]
}

final case class ArtifactOptions(
  @Help("Fetch source artifacts")
  @Short("S")
    sources: Boolean = false,
  @Help("Fetch javadoc artifacts")
  @Short("D")
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
  def artifactTypes(): Set[Type] =
    artifactTypes(sources = false, javadoc = false, default = true)
  def artifactTypes(sources: Boolean, javadoc: Boolean, default: Boolean): Set[Type] = {

    val types0 = artifactType
      .flatMap(_.split(','))
      .filter(_.nonEmpty)
      .map(Type(_))
      .toSet

    if (types0.isEmpty) {
      val sourceTypes = Some(Type.source).filter(_ => sources).toSet
      val javadocTypes = Some(Type.doc).filter(_ => javadoc).toSet
      val defaultTypes = if (default) ArtifactOptions.defaultArtifactTypes else Set()
      sourceTypes ++ javadocTypes ++ defaultTypes
    } else if (types0(Type("*")))
      Set(Type("*"))
    else
      types0
  }
}
