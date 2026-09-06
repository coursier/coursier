package coursierbuild

import java.io.File
import java.util.regex.Matcher

import mill.api.PathRef
import sttp.client4.Response
import sttp.client4.quick._

import scala.util.{Properties, Try}

/** Helpers around the standalone `cs.sh` launcher script, at the root of this repository */
object CsSh {

  def ghOrg  = GitHubReleaseAssets.ghOrg
  def ghName = GitHubReleaseAssets.ghName

  def defaultBranch = "main"

  private val csVersionRegex = """(?m)^CS_VERSION="([^"]*)"$""".r

  private def version(content: String, origin: String): String =
    csVersionRegex
      .findFirstMatchIn(content)
      .map(_.group(1))
      .getOrElse {
        sys.error(s"Could not find CS_VERSION in $origin")
      }

  def version(csSh: os.Path): String =
    version(os.read(csSh), csSh.toString)

  private def withVersion(content: String, newVersion: String): String =
    csVersionRegex.replaceAllIn(
      content,
      Matcher.quoteReplacement(s"""CS_VERSION="$newVersion"""")
    )

  /** Command running a bash able to run `cs.sh`
    *
    * On Windows, `bash` on the `PATH` is usually the WSL one, from `System32`, that can't run
    * `cs.sh` (and errors out straightaway if no distribution is installed). We use the bash of Git
    * for Windows instead, like GitHub Actions does for its `bash` shell.
    */
  private lazy val bashCommand: String =
    if (Properties.isWin) {
      val programFiles = sys.env.getOrElse("ProgramFiles", """C:\Program Files""")
      val gitBash      = os.Path(programFiles) / "Git" / "bin" / "bash.exe"
      val fromPath = sys.env
        .getOrElse("PATH", "")
        .split(File.pathSeparator)
        .iterator
        .filter(_.nonEmpty)
        .flatMap(dir => Try(os.Path(dir)).toOption.iterator)
        .filter(dir => !dir.segments.exists(_.equalsIgnoreCase("System32")))
        .map(_ / "bash.exe")
      (Iterator(gitBash) ++ fromPath)
        .find(os.isFile(_))
        .map(_.toString)
        .getOrElse {
          sys.error("No bash found, cs.sh needs the bash of Git for Windows to run")
        }
    }
    else "bash"

  /** Checks that `cs.sh` downloads and runs the coursier version it pins
    *
    * @param csSh
    *   Path to the `cs.sh` script to test
    * @param fetchModule
    *   A module, like `"org:name:version"`, that `cs.sh` is asked to fetch
    * @param workDir
    *   Directory to work in, used as home directory too, so that `cs.sh` starts from an empty
    *   launcher cache
    * @param versionOverride
    *   If set, test a copy of `cs.sh` pinning that version, rather than `cs.sh` itself
    */
  def test(
    csSh: os.Path,
    fetchModule: String,
    workDir: os.Path,
    versionOverride: Option[String] = None
  ): Unit = {

    // Start from an empty cache, so that the first run below actually downloads the cs launcher,
    // and so that we don't add it to the coursier cache shared with the other CI jobs.
    os.remove.all(workDir)
    val homeDir = workDir / "home"
    os.makeDir.all(homeDir)

    val script = versionOverride match {
      case None => csSh
      case Some(newVersion) =>
        val dest = workDir / csSh.last
        os.write(dest, withVersion(os.read(csSh), newVersion))
        dest
    }

    val pinnedVersion = version(script)
    System.err.println(s"cs.sh has CS_VERSION=$pinnedVersion")
    // the nightly release gets new launchers pushed to it, so we can't tell beforehand
    // which version they are going to report
    val expectedVersionOpt = Some(pinnedVersion).filter(_ != "nightly")

    val extraEnv =
      if (Properties.isWin) Map("LOCALAPPDATA" -> (homeDir / "AppData" / "Local").toString)
      else Map("HOME"                          -> homeDir.toString)

    // cs.sh is a bash script, and can't be run as is on Windows
    val csShArg =
      if (Properties.isWin) script.toString.replace("\\", "/")
      else script.toString

    def checkVersion(step: String): String = {
      val res = os.proc(bashCommand, csShArg, "version").call(
        cwd = workDir,
        env = extraEnv,
        stdout = os.Pipe,
        stderr = os.Pipe
      )
      val output = res.out.trim()
      System.err.print(res.err.text())
      for (expectedVersion <- expectedVersionOpt if output != expectedVersion)
        sys.error(s"$step: expected cs.sh to run coursier $expectedVersion, got '$output'")
      if (output.isEmpty)
        sys.error(s"$step: cs.sh printed no coursier version")
      System.err.println(s"OK ($step): cs.sh runs coursier $output")
      res.err.text()
    }

    val firstRunOutput = checkVersion("first run")
    if (!firstRunOutput.contains("Downloading"))
      sys.error("Expected the first cs.sh run to download the cs launcher")

    val secondRunOutput = checkVersion("second run")
    if (secondRunOutput.contains("Downloading"))
      sys.error("Expected the second cs.sh run to use the cs launcher cached by the first one")
    System.err.println("OK (second run): cs.sh used the cached cs launcher")

    // check that arguments are passed to the cs launcher, and that it can actually run things
    os.proc(bashCommand, csShArg, "fetch", fetchModule).call(
      cwd = workDir,
      env = extraEnv,
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )
    System.err.println(s"OK: cs.sh fetch $fetchModule")
  }

  /** Opens a pull request bumping `CS_VERSION` in `cs.sh`
    *
    * Does nothing if `cs.sh` on `baseBranch` already pins `newVersion`. If a pull request is
    * already open for that version, its branch is updated rather than a new one being opened.
    *
    * @param newVersion
    *   The coursier version `cs.sh` should download
    * @param ghTokenOpt
    *   GitHub token, used to push the branch and open the pull request
    * @param dryRun
    *   Whether to run a dry run (updating `cs.sh` in the clone below, but neither pushing it nor
    *   opening a pull request)
    * @param cloneUnder
    *   Directory to clone the repository under
    */
  def updateVersion(
    newVersion: String,
    ghTokenOpt: Option[String],
    dryRun: Boolean,
    cloneUnder: os.Path,
    baseBranch: String = defaultBranch
  ): Unit = {

    if (newVersion.endsWith("SNAPSHOT"))
      sys.error(s"Not updating cs.sh to snapshot version $newVersion")

    val remote = s"https://${ghTokenOpt.fold("")(_ + "@")}github.com/$ghOrg/$ghName.git"
    def masked(input: String): String =
      ghTokenOpt.fold(input)(token => input.replace(token, "****"))

    os.remove.all(cloneUnder)
    os.makeDir.all(cloneUnder)

    System.err.println(s"Cloning ${masked(remote)} in $cloneUnder")
    os.proc(
      "git",
      "clone",
      remote,
      "-q",
      "--depth",
      "1",
      "-b",
      baseBranch,
      PathRef.toResolvedPathString(cloneUnder)
    )
      .call(stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit)

    if (!os.exists(cloneUnder / ".git"))
      sys.error(s"Error: $ghOrg/$ghName not cloned in $cloneUnder")

    def git(args: String*): Unit =
      os.proc("git", args).call(
        cwd = cloneUnder,
        stdin = os.Inherit,
        stdout = os.Inherit,
        stderr = os.Inherit
      )

    val csSh           = cloneUnder / "cs.sh"
    val content        = os.read(csSh)
    val currentVersion = version(content, s"cs.sh on $baseBranch")

    if (currentVersion == newVersion)
      System.err.println(s"cs.sh on $baseBranch already has CS_VERSION=$newVersion, nothing to do")
    else {
      System.err.println(s"Updating CS_VERSION in cs.sh from $currentVersion to $newVersion")
      os.write.over(csSh, withVersion(content, newVersion))

      val branch = s"update-cs-sh/v$newVersion"

      git("config", "user.name", "Github Actions")
      git("config", "user.email", "actions@github.com")
      git("checkout", "-q", "-b", branch)
      git("add", "--", "cs.sh")
      git("commit", "-q", "-m", s"Update cs.sh to $newVersion")

      if (dryRun)
        System.err.println("Dry run, not pushing changes nor opening a pull request")
      else {
        val ghToken = ghTokenOpt.getOrElse {
          sys.error("No GitHub token passed")
        }
        System.err.println(s"Pushing $branch")
        git("push", "-q", "--force", "origin", s"HEAD:refs/heads/$branch")
        ensurePullRequest(branch, baseBranch, currentVersion, newVersion, ghToken)
      }
    }
  }

  private def ensurePullRequest(
    branch: String,
    baseBranch: String,
    currentVersion: String,
    newVersion: String,
    ghToken: String
  ): Unit = {

    def request = quickRequest
      .header("Accept", "application/vnd.github.v3+json")
      .header("Authorization", s"token $ghToken")

    def json(resp: Response[String], description: String): ujson.Value =
      if (resp.code.isSuccess) ujson.read(resp.body)
      else sys.error(s"Error $description: got HTTP ${resp.code.code}, response: ${resp.body}")

    val head = s"$ghOrg:$branch"
    val openPullRequests = request
      .get(uri"https://api.github.com/repos/$ghOrg/$ghName/pulls?head=$head&state=open")
      .send()

    val openPullRequestOpt = json(openPullRequests, s"listing open pull requests from $head")
      .arr
      .headOption
      .map(_("number").num.toInt)

    openPullRequestOpt match {
      case Some(number) =>
        System.err.println(s"Pull request #$number already open for $branch, updated it")
      case None =>
        val payload = ujson.Obj(
          "title" -> s"Update cs.sh to $newVersion",
          "head"  -> branch,
          "base"  -> baseBranch,
          "body" ->
            s"""Bumps `CS_VERSION` in `cs.sh` from `$currentVersion` to `$newVersion`, released in
               |https://github.com/$ghOrg/$ghName/releases/tag/v$newVersion.
               |
               |Opened automatically by the `updateCsShVersion` task.
               |""".stripMargin
        )
        val resp = request
          .body(payload.render())
          .post(uri"https://api.github.com/repos/$ghOrg/$ghName/pulls")
          .send()
        val number = json(resp, s"opening a pull request from $branch")("number").num.toInt
        System.err.println(s"Opened pull request #$number")
    }
  }
}
