package coursierbuild

import java.util.regex.Matcher

import mill.api.PathRef
import sttp.client4.Response
import sttp.client4.quick._

import scala.util.matching.Regex

/** A coursier version pinned in a file of this repository
  *
  * Those pins point at released coursier launchers or artifacts, so they have to be bumped after
  * each release - see [[VersionPin.update]], called from CI once the launchers of a tag have been
  * uploaded.
  */
trait VersionPin {

  /** Path of the file pinning the version, relative to the repository root */
  def relPath: os.SubPath

  /** Name of what is pinned, like `"CS_VERSION"`, used in messages and pull requests */
  def what: String

  /** Prefix of the branches pull requests bumping this version are opened from */
  def branchPrefix: String

  /** Name of the mill task opening those pull requests */
  def taskName: String

  /** Commit message and title of those pull requests */
  def title(newVersion: String): String

  /** Regex matching the line pinning the version, with whatever comes before the version as first
    * group, and the version itself, double quoted, as second group
    */
  protected def versionRegex: Regex

  def version(content: String, origin: String): String =
    versionRegex
      .findFirstMatchIn(content)
      .map(_.group(2))
      .getOrElse {
        sys.error(s"Could not find $what in $origin")
      }

  def version(file: os.Path): String =
    version(os.read(file), file.toString)

  def withVersion(content: String, newVersion: String): String =
    versionRegex.replaceAllIn(
      content,
      m => Matcher.quoteReplacement(s"""${m.group(1)}"$newVersion"""")
    )
}

object VersionPin {

  def ghOrg  = GitHubReleaseAssets.ghOrg
  def ghName = GitHubReleaseAssets.ghName

  def defaultBranch = "main"

  /** Opens a pull request bumping the version [[pin]] pins
    *
    * Does nothing if the file on `baseBranch` already pins `newVersion`. If a pull request is
    * already open for that version, its branch is updated rather than a new one being opened.
    *
    * @param pin
    *   The version pin to update
    * @param newVersion
    *   The coursier version to pin
    * @param ghTokenOpt
    *   GitHub token, used to push the branch and open the pull request
    * @param dryRun
    *   Whether to run a dry run (updating the file in the clone below, but neither pushing it nor
    *   opening a pull request)
    * @param cloneUnder
    *   Directory to clone the repository under
    */
  def update(
    pin: VersionPin,
    newVersion: String,
    ghTokenOpt: Option[String],
    dryRun: Boolean,
    cloneUnder: os.Path,
    baseBranch: String = defaultBranch
  ): Unit = {

    if (newVersion.endsWith("SNAPSHOT"))
      sys.error(s"Not updating ${pin.relPath} to snapshot version $newVersion")

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

    val file           = cloneUnder / pin.relPath
    val content        = os.read(file)
    val currentVersion = pin.version(content, s"${pin.relPath} on $baseBranch")

    if (currentVersion == newVersion)
      System.err.println(
        s"${pin.relPath} on $baseBranch already has ${pin.what}=$newVersion, nothing to do"
      )
    else {
      System.err.println(
        s"Updating ${pin.what} in ${pin.relPath} from $currentVersion to $newVersion"
      )
      os.write.over(file, pin.withVersion(content, newVersion))

      val branch = s"${pin.branchPrefix}/v$newVersion"

      git("config", "user.name", "Github Actions")
      git("config", "user.email", "actions@github.com")
      git("checkout", "-q", "-b", branch)
      git("add", "--", pin.relPath.toString)
      git("commit", "-q", "-m", pin.title(newVersion))

      if (dryRun)
        System.err.println("Dry run, not pushing changes nor opening a pull request")
      else {
        val ghToken = ghTokenOpt.getOrElse {
          sys.error("No GitHub token passed")
        }
        System.err.println(s"Pushing $branch")
        git("push", "-q", "--force", "origin", s"HEAD:refs/heads/$branch")
        ensurePullRequest(pin, branch, baseBranch, currentVersion, newVersion, ghToken)
      }
    }
  }

  private def ensurePullRequest(
    pin: VersionPin,
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

    val head             = s"$ghOrg:$branch"
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
          "title" -> pin.title(newVersion),
          "head"  -> branch,
          "base"  -> baseBranch,
          "body"  ->
            s"""Bumps `${pin.what}` in `${pin.relPath}` from `$currentVersion` to `$newVersion`,
               |released in https://github.com/$ghOrg/$ghName/releases/tag/v$newVersion.
               |
               |Opened automatically by the `${pin.taskName}` task.
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
