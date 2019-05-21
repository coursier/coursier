package coursier.cli.install

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.cli.app.RawAppDescriptor

final case class InstallAppOptions(

  @Help("Repository - for multiple repositories, separate with comma and/or add this option multiple times (e.g. -r central,ivy2local -r sonatype:snapshots, or equivalently -r central,ivy2local,sonatype:snapshots)")
  @Value("maven|sonatype:$repo|ivy2local|bintray:$org/$repo|bintray-ivy:$org/$repo|typesafe:ivy-$repo|typesafe:$repo|sbt-plugin:$repo|ivy:$pattern")
  @Short("r")
    repository: List[String] = Nil,

  shared: List[String] = Nil,
  @Name("E")
    exclude: List[String] = Nil,
  `type`: String = "bootstrap",
  classifier: List[String] = Nil,
  artifactType: List[String] = Nil,
  @Short("M")
    mainClass: Option[String] = None,
  property: List[String] = Nil,
  javaOpt: List[String] = Nil,
  scalaVersion: Option[String] = None
) {
  def rawAppDescriptor: RawAppDescriptor =
    RawAppDescriptor(
      Nil,
      repository,
      shared,
      exclude,
      `type`,
      classifier,
      artifactType,
      mainClass,
      javaOpt ++ property.map("-D" + _),
      property.map { s =>
        s.split("=", 2) match {
          case Array(k, v) => k -> v
          case Array(k) => k -> ""
        }
      },
      scalaVersion.map(_.trim).filter(_.nonEmpty)
    )
}

object InstallAppOptions {
  implicit val parser = caseapp.core.parser.Parser[InstallAppOptions]
  implicit val help = caseapp.core.help.Help[InstallAppOptions]
}
