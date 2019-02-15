
import java.io.File
import java.nio.file._

import $file.website.Website, Website.{Mdoc, Relativize, Util}

lazy val version = Util.cached("version") {
  Util.outputOf(Seq("sbt", "export coreJVM/version"))
    .linesIterator
    .map(_.trim)
    .filter(_.nonEmpty)
    .toSeq
    .last
}

lazy val scalaVersion = {
  val f = Paths.get("project/ScalaVersion.scala")
  val b = Files.readAllBytes(f)
  val s = new String(b, "UTF-8").linesIterator.toList.map(_.trim).filter(_.contains("def scala212")) match {
    case h :: Nil => h
    case _ => ???
  }
  val prefix = """def scala212 = """"
  val suffix = "\""
  assert(s.startsWith(prefix))
  assert(s.endsWith(suffix))
  s.stripPrefix(prefix).stripSuffix(suffix)
}

lazy val pluginVersion = Util.cached("plugin-version") {
  Util.outputOf(Seq("sbt", "export getSbtCoursierVersion"))
    .linesIterator
    .map(_.trim)
    .filter(_.nonEmpty)
    .toSeq
    .last
}

lazy val mdocProps = {
  def extraSbt(v: String) =
    if (v.endsWith("SNAPSHOT"))
      """resolvers += Resolver.sonatypeRepo("snapshots")""" + "\n"
    else
      ""
  Map(
    "VERSION" -> version,
    "EXTRA_SBT" -> extraSbt(version),
    "PLUGIN_VERSION" -> pluginVersion,
    "PLUGIN_EXTRA_SBT" -> extraSbt(pluginVersion),
    "SCALA_VERSION" -> scalaVersion
  )
}

@main
def main(publishLocal: Boolean = false, npmInstall: Boolean = false, yarnRunBuild: Boolean = false, watch: Boolean = false, relativize: Boolean = false): Unit = {

  assert(!(watch && relativize), "Cannot specify both --watch and --relativize")

  if (publishLocal)
    // alternatively, we could try to get the full classpath of cacheJVM, and just inject it in the mdoc classpath
    Util.runCmd(Seq(
      "sbt",
      "set version in ThisBuild := \"" + version + "\"",
      "coreJVM/publishLocal",
      "cacheJVM/publishLocal",
      "coursierJVM/publishLocal"
    ))

  val websiteDir = new File("doc/website")

  val yarnRunBuildIn =
    if (yarnRunBuild)
      Some(websiteDir)
    else
      None

  if (npmInstall)
    Util.runCmd(Seq("npm", "install"), dir = websiteDir)

  val mdoc = new Mdoc(
    new File("doc/docs"),
    new File("doc/processed-docs"),
    scalaVersion,
    dependencies = Seq(s"io.get-coursier:coursier_2.12:$version"),
    mdocProps = mdocProps
  )

  if (watch)
    mdoc.watch(yarnRunStartIn = yarnRunBuildIn)
  else {
    mdoc.run(yarnRunBuildIn = yarnRunBuildIn)
    if (relativize)
      Relativize.relativize(websiteDir.toPath.resolve("build"))
  }
}

