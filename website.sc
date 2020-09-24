
// Ammonite 2.1.4-11-307f3d8, scala 2.12.12

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
def watch(skipGenerate: Boolean = false) = {

  // generate website once so that `yarn run start` starts with a valid website
  if (!skipGenerate)
    generate()

  Docusaurus.generate(
    docusaurusDir,
    "docs/mdoc",
    watch = true
  )
}

@main
def generate() =
  Docusaurus.generate(docusaurusDir, "docs/mdoc")

@main
def copyDemoFiles(): Unit = {
  Util.run(Seq("sbt", "web/fastOptJS::webpack"))
  val dest = new File(docusaurusDir, "build/coursier/demo")
  dest.mkdirs()

  Files.copy(
    Paths.get("modules/web/target/scala-2.12/scalajs-bundler/main/web-fastopt-bundle.js"),
    dest.toPath.resolve("web-fastopt-bundle.js"),
    java.nio.file.StandardCopyOption.REPLACE_EXISTING
  )

  Files.copy(
    Paths.get("modules/web/target/scala-2.12/classes/bundle.js"),
    dest.toPath.resolve("bundle.js"),
    java.nio.file.StandardCopyOption.REPLACE_EXISTING
  )

  val indexHtml = new String(Files.readAllBytes(Paths.get("modules/web/target/scala-2.12/classes/index.html")), "UTF-8")
    .replaceAll("../scalajs-bundler/main/", "")
  Files.write(dest.toPath.resolve("index.html"), indexHtml.getBytes("UTF-8"))
}

@main
def updateWebsite(): Unit = {

  val versionedDocsRepo = "coursier/versioned-docs"
  val versionedDocsBranch = "master"

  val token = if (dryRun) "" else ghToken()

  val versionOpt = Version.latestFromTravisTagOpt

  Docusaurus.getVersionedDocs(
    docusaurusDir,
    versionedDocsRepo,
    versionedDocsBranch
  )

  generate()

  for (v <- versionOpt)
    Docusaurus.updateVersionedDocs(
      docusaurusDir,
      versionedDocsRepo,
      versionedDocsBranch,
      ghTokenOpt = Some(token),
      newVersion = v,
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
