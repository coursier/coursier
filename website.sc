
import $file.scripts.shared.Docusaurus
import $file.scripts.shared.Util
import $file.scripts.shared.Version

import java.io.File
import java.nio.file.{Files, Paths}

private val docusaurusDir = new File("doc/website")

private def ghToken() = Option(System.getenv("GH_TOKEN")).getOrElse {
  sys.error("GH_TOKEN not set")
}

private val dryRun = false

@main
def generate() =
  Docusaurus.generate(docusaurusDir, "docs/mdoc")

@main
def updateVersionedDocs() = {

  val token = if (dryRun) "" else ghToken()

  val version = Version.latestFromTravisTag

  generate()

  Docusaurus.getOrUpdateVersionedDocs(
    docusaurusDir,
    "coursier/versioned-docs",
    ghTokenOpt = Some(token),
    newVersionOpt = Some(version),
    dryRun = dryRun
  )
}

@main
def copyDemoFiles(): Unit = {
  Util.run(Seq("sbt", "web/fastOptJS::webpack"))
  val dest = new File(docusaurusDir, "build/coursier/demo")
  dest.mkdirs()

  Files.copy(
    Paths.get("modules/web/target/scala-2.12/scalajs-bundler/main/web-fastopt-bundle.js"),
    dest.toPath.resolve("web-fastopt-bundle.js")
  )

  Files.copy(
    Paths.get("modules/web/target/scala-2.12/classes/bundle.js"),
    dest.toPath.resolve("bundle.js")
  )

  val indexHtml = new String(Files.readAllBytes(Paths.get("modules/web/target/scala-2.12/classes/index.html")), "UTF-8")
    .replaceAll("../scalajs-bundler/main/", "")
  Files.write(dest.toPath.resolve("index.html"), indexHtml.getBytes("UTF-8"))
}

@main
def updateWebsite(): Unit = {

  val token = if (dryRun) "" else ghToken()

  val version = Version.latestFromTravisTag

  generate()

  Docusaurus.getOrUpdateVersionedDocs(
    docusaurusDir,
    "coursier/versioned-docs",
    ghTokenOpt = Some(token),
    newVersionOpt = Some(version),
    dryRun = dryRun
  )

  copyDemoFiles()

  Docusaurus.updateGhPages(
    new File(docusaurusDir, "build"),
    token,
    "coursier/coursier",
    branch = "gh-pages",
    dryRun = dryRun
  )
}

@main
def dummy() = {}
