package coursier.cli.publish.sonatype.options

final case class SonatypeOptions(
  base: Option[String] = None,
  user: Option[String] = None,
  password: Option[String] = None
) {
  override def toString: String =
    Seq(
      base,
      user,
      password
    ).mkString("SonatypeOptions(", ", ", ")")
}
