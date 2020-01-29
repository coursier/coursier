
import $file.Relativize
import $file.Util

import java.io.File
import java.nio.file.Files

/**
 * Runs mdoc / docusaurus to generate a new website directory.
 *
 * The new website is generated under docusaurusDir/build
 */
def generate(
  docusaurusDir: File,
  sbtMdocCommand: String,
  npmInstall: Option[Boolean] = None,
  yarnRunBuild: Boolean = true,
  watch: Boolean = false,
  relativize: Boolean = true
): Unit = {

  assert(!(watch && relativize), "Cannot specify both --watch and --relativize")

  val yarnRunBuildIn =
    if (yarnRunBuild)
      Some(docusaurusDir)
    else
      None

  val npmInstall0 = npmInstall.getOrElse {
    val nodeModules = new File(docusaurusDir, "node_modules")
    !nodeModules.isDirectory
  }
  if (npmInstall0)
    Util.run(Seq("npm", "install"), docusaurusDir)

  if (watch) {
    def runMdoc() = Util.run(Seq("sbt", s"$sbtMdocCommand --watch"))
    yarnRunBuildIn match {
      case None =>
        runMdoc()
      case Some(d) =>
        Util.withBgProcess(Seq("yarn", "run", "start"), dir = d) {
          runMdoc()
        }
    }
  } else {
    Util.run(Seq("sbt", sbtMdocCommand))
    for (d <- yarnRunBuildIn)
      Util.run(Seq("yarn", "run", "build"), d)
    if (relativize)
      Relativize.relativize(docusaurusDir.toPath.resolve("build"))
  }
}

/**
 * Adds versioned docs from remote repo to local docusaurus directory.
 *
 * Optionally, marks a new version and pushes the updated versioned docs
 * to the remote repo.
 */
def getOrUpdateVersionedDocs(
  docusaurusDir: File,
  repo: String,
  ghTokenOpt: Option[String],
  newVersionOpt: Option[String],
  dryRun: Boolean
): Unit = {

  val remote = s"https://${ghTokenOpt.map(_ + "@").getOrElse("")}github.com/$repo.git"

  Util.withTmpDir("versioned-docs") { dest =>
    // FIXME The few "cp" commands make this not runnable on Windows I guess…

    Util.run(Seq("git", "clone", remote, "-b", "master", dest.toString))

    val versionedDocsDir = dest.resolve("versioned_docs")
    if (Files.exists(versionedDocsDir))
      Util.run(Seq("cp", "-R", versionedDocsDir.toString, docusaurusDir.getAbsolutePath + "/"))

    val versionedSidebarsDir = dest.resolve("versioned_sidebars")
    if (Files.exists(versionedSidebarsDir))
      Util.run(Seq("cp", "-R", versionedSidebarsDir.toString, docusaurusDir.getAbsolutePath + "/"))

    val versionsJson = dest.resolve("versions.json")
    if (Files.exists(versionsJson))
      Util.run(Seq("cp", versionsJson.toString, docusaurusDir.getAbsolutePath + "/"))

    for (v <- newVersionOpt) {
      // TODO Check if v is already in versions.json

      // FIXME We don't necessarily run on Travis CI
      Util.run(Seq("git", "config", "user.name", "Travis-CI"), dest.toFile)
      Util.run(Seq("git", "config", "user.email", "invalid@travis-ci.com"), dest.toFile)

      Util.run(Seq("yarn", "run", "version", v), docusaurusDir)

      val toCopy = docusaurusDir 
        .listFiles()
        .filter(_.getName.startsWith("version"))

      if (toCopy.nonEmpty)
        Util.run(Seq("cp", "-R") ++ toCopy.map(_.getAbsolutePath) ++ Seq(dest.toString))

      Util.run(Seq("git", "add") ++ toCopy.map(_.getName), dest.toFile)
      Util.run(Seq("git", "commit", "-m", s"Add doc for $v"), dest.toFile)
      if (dryRun)
        System.err.println(s"Would have pushed new docs to $repo")
      else
        Util.run(Seq("git", "push", "origin", "master"), dest.toFile)
    }
  }
}

/**
 * Pushes a local static website directory to a remote gh-pages like branch.
 *
 * @param siteDir Path to static website
 * @param ghToken GitHub token
 * @param repo GitHub repository, like `"org/name"`
 * @param dryRun Whether to run a dry run (print actions to be run, but don't upload / push anything)
 * @param branch Branch name to upload things to
 */
def updateGhPages(
  siteDir: File,
  ghToken: String,
  repo: String,
  dryRun: Boolean,
  branch: String = "gh-pages"
): Unit = {
  val remote = s"https://$ghToken@github.com/$repo.git"

  Util.withTmpDir("gh-pages") { dest =>
    Util.run(Seq("git", "clone", remote, "-q", "-b", branch, dest.toString))
    Util.run(Seq("git", "config", "user.name", "Travis-CI"), dest.toFile)
    Util.run(Seq("git", "config", "user.email", "invalid@travis-ci.com"), dest.toFile)

    val keepList = {
      val f = dest.resolve(".keep")
      if (Files.isRegularFile(f))
        new String(Files.readAllBytes(f), "UTF-8")
          .linesIterator
          .toVector
          .filter(_.nonEmpty)
          .toSet
      else
        Set.empty[String]
    }

    val toGitRm = dest
      .toFile
      .list()
      .filter(!_.startsWith("."))
      .filter(!keepList.contains(_))

    if (toGitRm.nonEmpty)
      Util.run(Seq("git", "rm", "-r") ++ toGitRm, dest.toFile)

    System.err.println("Copying new website")

    val toCopy = siteDir 
      .listFiles()
      .filter(!_.getName.startsWith("."))
      .flatMap(_.listFiles())
      .filter(!_.getName.startsWith("."))
      .map(_.getAbsolutePath)

    Util.run(Seq("cp", "-pR") ++ toCopy ++ Seq(dest.toString))
    Util.run(Seq("git", "add", "--", "."), dest.toFile)

    val hasChanges = Util.gitRepoHasChanges(dest.toFile)
    if (hasChanges) {
      Util.run(Seq("git", "commit", "-m", "Update website"), dest.toFile)
      if (dryRun)
        System.err.println("Dummy mode, not pushing changes")
      else
        Util.run(Seq("git", "push", "origin", branch), dest.toFile)
    } else
      System.err.println("Nothing changed")
  }
}

