package coursierbuild

object DocHelpers {
  def copyDocusaurusVersionedData(
    repo: String,
    branch: String,
    docusaurusDir: os.Path,
    cloneUnder: os.Path
  ): Unit = {

    val remote = s"https://github.com/$repo.git"

    os.proc("git", "clone", remote, "-b", branch, cloneUnder.toString).call(
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )

    val versionedDocsDir     = cloneUnder / "versioned_docs"
    val versionedSidebarsDir = cloneUnder / "versioned_sidebars"
    val versionsJson         = cloneUnder / "versions.json"

    if (os.exists(versionedDocsDir)) {
      val dest = docusaurusDir / "versioned_docs"
      os.remove.all(dest)
      os.copy(versionedDocsDir, dest)
    }
    if (os.exists(versionedSidebarsDir)) {
      val dest = docusaurusDir / "versioned_sidebars"
      os.remove.all(dest)
      os.copy(versionedSidebarsDir, dest)
    }
    if (os.exists(versionsJson)) {
      val dest = docusaurusDir / "versions.json"
      os.remove.all(dest)
      os.copy(versionsJson, dest)
    }
  }

  private def runAndLog(secrets: Seq[String])(command: os.Shellable*) = {
    val msg = secrets.foldLeft(s"Running ${command.flatMap(_.value)}") { (msg0, secret) =>
      msg0.replace(secret, "****")
    }
    System.err.println(msg)
    os.proc(command: _*)
  }

  def updateVersionedDocs(
    docusaurusDir: os.Path,
    repo: String,
    branch: String,
    ghTokenOpt: Option[String],
    newVersion: String,
    dryRun: Boolean,
    cloneUnder: os.Path
  ): Unit = {

    val remote = s"https://${ghTokenOpt.map(_ + "@").getOrElse("")}github.com/$repo.git"

    os.makeDir.all(cloneUnder)

    System.err.println()
    runAndLog(ghTokenOpt.toSeq)("git", "clone", remote, "-b", branch, cloneUnder.toString).call(
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )

    // TODO Check if newVersion is already in versions.json

    // FIXME We don't necessarily run on Travis CI
    runAndLog(ghTokenOpt.toSeq)("git", "config", "user.name", "Github Actions").call(
      cwd = cloneUnder,
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )
    runAndLog(ghTokenOpt.toSeq)("git", "config", "user.email", "actions@github.com").call(
      cwd = cloneUnder,
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )

    runAndLog(ghTokenOpt.toSeq)("yarn", "run", "version", newVersion).call(
      cwd = docusaurusDir,
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )

    val toCopy = os.list(docusaurusDir).filter(_.last.startsWith("version"))

    for (elem <- toCopy) {
      val dest = cloneUnder / elem.last
      if (os.exists(dest))
        os.remove.all(dest)
      os.copy(elem, dest)
    }

    runAndLog(ghTokenOpt.toSeq)("git", "add", toCopy.map(_.last)).call(
      cwd = cloneUnder,
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )

    runAndLog(ghTokenOpt.toSeq)("git", "commit", "-m", s"Add doc for $newVersion").call(
      cwd = cloneUnder,
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )
    if (dryRun)
      System.err.println(s"Would have pushed new docs to $repo")
    else
      runAndLog(ghTokenOpt.toSeq)("git", "push", "origin", branch).call(
        cwd = cloneUnder,
        stdin = os.Inherit,
        stdout = os.Inherit,
        stderr = os.Inherit
      )

    ()
  }

  def updateGhPages(
    siteDir: os.Path,
    ghToken: String,
    repo: String,
    dryRun: Boolean,
    dest: os.Path,
    branch: String = "gh-pages"
  ): Unit = {
    val remote = s"https://$ghToken@github.com/$repo.git"

    os.makeDir.all(dest)

    os.proc("git", "clone", remote, "-q", "-b", branch, dest.toString).call(
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )
    os.proc("git", "config", "user.name", "Github Actions").call(
      cwd = dest,
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )
    os.proc("git", "config", "user.email", "actions@github.com").call(
      cwd = dest,
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )

    val keepList = {
      val f = dest / ".keep"
      if (os.isFile(f))
        os.read(f)
          .linesIterator
          .toVector
          .filter(_.nonEmpty)
          .toSet
      else
        Set.empty[String]
    }

    val toGitRm = os.list(dest)
      .filter(!_.last.startsWith("."))
      .filter(f => !keepList.contains(f.last))

    if (toGitRm.nonEmpty)
      os.proc("git", "rm", "-r", toGitRm).call(
        cwd = dest,
        stdin = os.Inherit,
        stdout = os.Inherit,
        stderr = os.Inherit
      )

    System.err.println("Copying new website")

    val toCopy = os.list(siteDir)
      .filter(!_.last.startsWith("."))
      .flatMap(os.list(_))
      .filter(!_.last.startsWith("."))

    for (elem <- toCopy)
      os.copy.into(elem, dest)

    os.proc("git", "add", "--", ".").call(
      cwd = dest,
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )

    val hasChanges = gitRepoHasChanges(dest)
    if (hasChanges) {
      os.proc("git", "commit", "-m", "Update website").call(
        cwd = dest,
        stdin = os.Inherit,
        stdout = os.Inherit,
        stderr = os.Inherit
      )
      if (dryRun)
        System.err.println("Dummy mode, not pushing changes")
      else
        os.proc("git", "push", "origin", branch).call(
          cwd = dest,
          stdin = os.Inherit,
          stdout = os.Inherit,
          stderr = os.Inherit
        )
    }
    else
      System.err.println("Nothing changed")

    ()
  }

  def gitRepoHasChanges(repo: os.Path): Boolean = {
    val res = os.proc("git", "status").call(
      cwd = repo,
      stdin = os.Inherit,
      stdout = os.Pipe,
      stderr = os.Pipe,
      mergeErrIntoOut = true
    )
    val output = res.out.text()
    !output.contains("nothing to commit")
  }
}
