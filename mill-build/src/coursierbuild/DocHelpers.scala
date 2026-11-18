package coursierbuild

object DocHelpers {

  /** Environment variables holding secrets, that third-party tools (npm and the packages it
    * installs, …) building the website shouldn't see
    */
  val secretEnvVars: Seq[String] = Seq("GH_TOKEN", "UPLOAD_GH_TOKEN")

  /** To be passed as `env` to `os.proc(…).call`, removes [[secretEnvVars]] from the environment of
    * the process (os-lib removes variables whose value is `null`)
    */
  def withoutSecretsEnv: Map[String, String] =
    secretEnvVars.map(_ -> (null: String)).toMap

  /** Removes [[secretEnvVars]] from `env`, for processes started with `propagateEnv = false` */
  def withoutSecrets(env: Map[String, String]): Map[String, String] =
    env -- secretEnvVars

  /** Pages of the former mkdocs-based website, that used to be published under `/upcoming`, and the
    * page under `/docs` their content was merged into
    */
  private def upcomingPages: Seq[(String, String)] = Seq(
    "index"                   -> "overview",
    "features"                -> "overview",
    "features-cache"          -> "cache",
    "features-archive-cache"  -> "features-archive-cache",
    "features-jvm"            -> "features-jvm",
    "features-bom"            -> "features-bom",
    "features-gradle-modules" -> "features-gradle-modules",
    "features-docker"         -> "overview",
    "api"                     -> "api",
    "api-dependency"          -> "api-dependency",
    "api-projects"            -> "api-projects",
    "api-cache"               -> "api-cache",
    "api-archive-cache"       -> "api-archive-cache",
    "api-docker"              -> "api",
    "java-api"                -> "java-api",
    "cli"                     -> "cli-overview",
    "cli-dependency"          -> "cli-dependency",
    "cli-cache"               -> "cli-cache",
    "cli-archive-cache"       -> "cli-archive-cache",
    "cli-binaries"            -> "cli-installation",
    "cli-report"              -> "json-report",
    "cli-docker"              -> "cli-overview",
    "cli-ci-script"           -> "cli-installation",
    "gha"                     -> "overview",
    "gha-setup-action"        -> "overview",
    "gha-cache-action"        -> "overview",
    "reference-repositories"  -> "other-repositories",
    "reference-mirrors"       -> "other-mirrors",
    "contrib"                 -> "dev-contributing",
    "about-community"         -> "about-community",
    "about-funding"           -> "about-funding",
    "about-contact"           -> "about-contact",
    "about-security"          -> "about-security"
  )

  /** Writes pages redirecting the URLs of the former mkdocs-based website (`/upcoming/…`) to the
    * pages its content was merged into, under `siteDir`, the root of the built website
    *
    * Fragments (`#some-section`) are kept, as most headings were kept as is.
    */
  def writeUpcomingRedirects(siteDir: os.Path): Unit =
    for ((page, doc) <- upcomingPages) {
      val target = s"/docs/$doc"
      val content =
        s"""<!DOCTYPE html>
           |<html lang="en">
           |<head>
           |<meta charset="utf-8">
           |<title>Redirecting…</title>
           |<link rel="canonical" href="https://get-coursier.io$target">
           |<meta name="robots" content="noindex">
           |<script>location.replace("$target" + location.hash)</script>
           |<meta http-equiv="refresh" content="0; url=$target">
           |</head>
           |<body>This page moved to <a href="$target">$target</a>.</body>
           |</html>
           |""".stripMargin
      val dest =
        if (page == "index") siteDir / "upcoming/index.html"
        else siteDir / "upcoming" / page / "index.html"
      os.write.over(dest, content, createFolders = true)
    }

  def copyDocusaurusVersionedData(
    repo: String,
    branch: String,
    docusaurusDir: os.Path,
    cloneUnder: os.Path
  ): Unit = {

    val remote = s"https://github.com/$repo.git"

    os.makeDir.all(cloneUnder)

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

    migrateVersionedDocs(docusaurusDir)
  }

  /** Converts the versioned docs under `docusaurusDir` from the Docusaurus 1 format to the
    * Docusaurus 3 one, in place
    *
    * Docusaurus 1 only snapshots the docs and the sidebar that changed since the former version,
    * and the missing ones fall back to the latest former version having them. Its docs have
    * `id: version-X-foo` / `original_id: foo` front matters, and its sidebars refer to docs with
    * that `version-X-` prefix too.
    *
    * Docusaurus 3 needs a full snapshot for each version, and doc ids without a version prefix.
    *
    * Versions already in the Docusaurus 3 format (added by `docusaurus docs:version`, or migrated
    * by an earlier run, then pushed to coursier/versioned-docs) are left as is.
    */
  def migrateVersionedDocs(docusaurusDir: os.Path): Unit = {

    val versionsJson = docusaurusDir / "versions.json"
    val versions =
      if (os.isFile(versionsJson)) ujson.read(os.read(versionsJson)).arr.map(_.str).toVector
      else Vector.empty[String]

    def isV1Doc(content: String): Boolean =
      frontMatterLines(content).exists(_.exists(_.startsWith("original_id:")))

    def migrateDoc(content: String): String =
      frontMatterLines(content) match {
        case Some(lines) =>
          val body = content.linesWithSeparators.drop(lines.length + 2).mkString
          val newLines = lines.filter { line =>
            !line.startsWith("id:") && !line.startsWith("original_id:")
          }
          (Seq("---") ++ newLines ++ Seq("---")).map(_ + "\n").mkString + body
        case None => content
      }

    var docs          = Map.empty[os.SubPath, String]
    var sidebarOpt    = Option.empty[ujson.Value]
    var migratedCount = 0

    // Docusaurus 1 falls back to older versions, which are last in versions.json
    for (version <- versions.reverseIterator) {
      val docsDir     = docusaurusDir / "versioned_docs" / s"version-$version"
      val sidebarFile = docusaurusDir / "versioned_sidebars" / s"version-$version-sidebars.json"

      val versionDocs =
        if (os.isDir(docsDir))
          os.walk(docsDir)
            .filter(os.isFile)
            .filter(_.last.endsWith(".md"))
            .map(f => f.subRelativeTo(docsDir) -> os.read(f))
            .toMap
        else
          Map.empty[os.SubPath, String]

      val isV1 = versionDocs.isEmpty || versionDocs.valuesIterator.exists(isV1Doc)

      docs =
        if (isV1) docs ++ versionDocs.map { case (p, content) => p -> migrateDoc(content) }
        else versionDocs

      val docIds = docs.keySet.map(_.toString.stripSuffix(".md"))

      if (os.isFile(sidebarFile))
        sidebarOpt = Some(stripVersionPrefix(ujson.read(os.read(sidebarFile)), version))
      for (sidebar <- sidebarOpt)
        os.write.over(
          sidebarFile,
          ujson.write(removeMissingDocs(sidebar, docIds), indent = 2) + "\n"
        )

      if (isV1) {
        migratedCount += 1
        for ((p, content) <- docs)
          os.write.over(docsDir / p, content, createFolders = true)
      }
    }

    if (migratedCount > 0)
      System.err.println(
        s"Migrated $migratedCount versioned docs out of ${versions.length} to Docusaurus 3"
      )
  }

  /** Removes the `version-X-` prefixes from a Docusaurus 1 sidebar (no-op on Docusaurus 3 ones) */
  private def stripVersionPrefix(sidebar: ujson.Value, version: String): ujson.Value = {
    val prefix = s"version-$version-"
    sidebar match {
      case ujson.Str(id)    => ujson.Str(id.stripPrefix(prefix))
      case ujson.Arr(items) => ujson.Arr(items.map(stripVersionPrefix(_, version)))
      case ujson.Obj(entries) =>
        ujson.Obj.from(
          entries.iterator.map {
            case (k, v) => k.stripPrefix(prefix) -> stripVersionPrefix(v, version)
          }
        )
      case other => other
    }
  }

  /** Removes the sidebar entries pointing at missing docs, that Docusaurus 1 ignores and that make
    * Docusaurus 3 fail
    */
  private def removeMissingDocs(sidebar: ujson.Value, docIds: Set[String]): ujson.Value =
    sidebar match {
      case ujson.Arr(items) =>
        ujson.Arr(
          items
            .filter {
              case ujson.Str(id) => docIds.contains(id)
              case _             => true
            }
            .map(removeMissingDocs(_, docIds))
        )
      case ujson.Obj(entries) =>
        ujson.Obj.from(entries.iterator.map { case (k, v) => k -> removeMissingDocs(v, docIds) })
      case other => other
    }

  /** The lines of the front matter of a Markdown file, if it has one */
  private def frontMatterLines(content: String): Option[Seq[String]] = {
    val lines = content.linesIterator
    if (lines.hasNext && lines.next() == "---")
      Some(lines.takeWhile(_ != "---").toVector)
    else
      None
  }

  private def runAndLog(secrets: Seq[String])(command: os.Shellable*) = {
    val msg = secrets.foldLeft(s"Running ${command.flatMap(_.value)}") { (msg0, secret) =>
      msg0.replace(secret, "****")
    }
    System.err.println(msg)
    os.proc(command *)
  }

  /** Snapshots the docs of `newVersion` with `docusaurus docs:version`, under `docusaurusDir` */
  def addVersionedDocs(docusaurusDir: os.Path, newVersion: String): Unit = {
    runAndLog(Nil)("yarn", "run", "version", newVersion).call(
      cwd = docusaurusDir,
      env = withoutSecretsEnv,
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )
    ()
  }

  /** Pushes the versioned docs under `docusaurusDir` (see [[addVersionedDocs]]) to `repo` */
  def pushVersionedDocs(
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
    runAndLog(ghTokenOpt.toSeq)(
      "git",
      "clone",
      remote,
      "-b",
      branch,
      cloneUnder.toString
    ).call(
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

    if (!os.exists(dest / ".git"))
      sys.error(s"Error: $repo not cloned in $dest")

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
      os.proc("git", "rm", "-r", "-q", "--", toGitRm.map(_.last)).call(
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

    // Prevents GitHub Pages from processing the website with Jekyll, that ignores files starting
    // with '_'
    os.write.over(dest / ".nojekyll", "")

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
