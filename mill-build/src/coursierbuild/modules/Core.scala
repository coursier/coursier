package coursierbuild.modules

import coursierbuild.Deps.Deps
import mill._

trait Core extends CsModule with CsCrossJvmJsModule with CoursierPublishModule {
  def artifactName   = "coursier-core"
  def compileIvyDeps = super.compileIvyDeps() ++ Agg(
    Deps.dataClass,
    Deps.jsoniterMacros
  )
  def ivyDeps = super.ivyDeps() ++ Agg(
    Deps.fastParse,
    Deps.jsoniterCore,
    Deps.versions
  )

  def commitHash: T[String]

  def constantsFile = Task {
    val dest = Task.dest / "Properties.scala"
    val code =
      s"""package coursier.util
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
