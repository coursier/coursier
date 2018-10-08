package coursier.cli.options

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.core.{Resolution, Type}

object ArtifactOptions {
  def defaultArtifactTypes = Resolution.defaultTypes

  implicit val parser = Parser[ArtifactOptions]
  implicit val help = caseapp.core.help.Help[ArtifactOptions]
}

final case class ArtifactOptions(
  @Help("Artifact types that should be retained (e.g. jar, src, doc, etc.) - defaults to jar,bundle")
  @Value("type1,type2,...")
  @Short("A")
    artifactType: List[String] = Nil,
  @Help("Fetch artifacts even if the resolution is errored")
    force: Boolean = false
) {
  def artifactTypes(sources: Boolean, javadoc: Boolean): Set[Type] = {

    val types0 = artifactType
      .flatMap(_.split(','))
      .filter(_.nonEmpty)
      .map(Type(_))
      .toSet

    if (types0.isEmpty) {
      if (sources || javadoc)
        Some(Type.source).filter(_ => sources).toSet ++ Some(Type.doc).filter(_ => javadoc)
      else
        ArtifactOptions.defaultArtifactTypes
    } else if (types0(Type("*")))
      Set(Type("*"))
    else
      types0
  }
}
