package coursier.cli.publish.options

// format: off
final case class DirectoryOptions(
  dir: List[String] = Nil,
  sbtDir: List[String] = Nil,
  sbt: Boolean = false
)
// format: on
