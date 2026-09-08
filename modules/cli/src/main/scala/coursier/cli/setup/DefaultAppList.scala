package coursier.cli.setup

object DefaultAppList {

  def defaultAppList: Seq[String] =
    Seq(
      "cs",
      "coursier",
      "scala",
      "scalac",
      "scala-cli",
      "sbt",
      "sbtn",
      "scalafmt"
    )

}
