package coursier.cli.publish.options

final case class DirectoryOptions(
  dir: List[String] = Nil,
  sbtDir: List[String] = Nil,
  sbt: Boolean = false
)
