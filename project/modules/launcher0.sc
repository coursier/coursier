import $file.shared, shared.{CoursierPublishModule, CsMima, CsModule, commitHash}

import mill._, mill.scalalib._

trait LauncherBase extends CsModule with CrossSbtModule with CoursierPublishModule with CsMima {
  def artifactName = "coursier-launcher"

  def bootstrap: T[PathRef]
  def resourceBootstrap: T[PathRef]
  def noProguardBootstrap: T[PathRef]
  def noProguardResourceBootstrap: T[PathRef]

  def resources = T.sources {

    val dir = T.dest / "resources"

    val files = Seq(
      "bootstrap-orig.jar"           -> noProguardBootstrap(),
      "bootstrap.jar"                -> bootstrap(),
      "bootstrap-resources-orig.jar" -> noProguardResourceBootstrap(),
      "bootstrap-resources.jar"      -> resourceBootstrap()
    )

    os.makeDir.all(dir)
    for ((destName, ref) <- files)
      os.copy(ref.path, dir / destName)

    super.resources() :+ PathRef(dir)
  }

  def constantsFile = T {
    val dest = T.dest / "Properties.scala"
    val code =
      s"""package coursier.launcher.internal
         |
         |/** Build-time constants. Generated from mill. */
         |object Properties {
         |  def version = "${publishVersion()}"
         |  def commitHash = "${commitHash()}"
         |}
         |""".stripMargin
    os.write(dest, code)
    PathRef(dest)
  }
  def generatedSources = super.generatedSources() ++ Seq(constantsFile())
}
