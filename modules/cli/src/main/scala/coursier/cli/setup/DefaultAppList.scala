package coursier.cli.setup

object DefaultAppList {

  def defaultAppList: Seq[String] =
    Seq(
      "ammonite",
      "cs",
      "coursier",
      "scala",
      "scalac",
      "sbt-launcher",
      "scalafmt"
    )

}
