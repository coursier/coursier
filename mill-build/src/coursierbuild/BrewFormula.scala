package coursierbuild

import coursierbuild.DocHelpers.gitRepoHasChanges

import java.security.MessageDigest

/** Updates the formula of https://github.com/coursier/homebrew-formulas, generated from
  * `.github/scripts/coursier.rb.template`, with the launchers of a release
  */
object BrewFormula {

  def repo   = "coursier/homebrew-formulas"
  def branch = "master"

  private def sha256(path: os.Path): String =
    MessageDigest.getInstance("SHA-256")
      .digest(os.read.bytes(path))
      .map(b => f"${b & 0xff}%02x")
      .mkString

  /** Generates the formula of `version` from `template`, and pushes it to [[repo]]
    *
    * The launchers of `version` must have been uploaded to its GitHub release already. The repo is
    * cloned under `workDir`, which must not contain a former clone. When `dryRun` is true, the
    * changes are committed but not pushed, and `ghToken` isn't used.
    */
  def update(
    version: String,
    template: os.Path,
    ghToken: String,
    dryRun: Boolean,
    workDir: os.Path
  ): Unit = {

    val repoDir = workDir / "homebrew-formulas"
    val dlDir   = workDir / "downloads"
    os.makeDir.all(dlDir)

    val remote =
      if (dryRun) s"https://github.com/$repo.git"
      else s"https://$ghToken@github.com/$repo.git"

    System.err.println(s"Cloning https://github.com/$repo.git in $repoDir")
    os.proc("git", "clone", remote, "-q", "-b", branch, repoDir.toString).call(
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )

    os.proc("git", "config", "user.name", "Github Actions").call(
      cwd = repoDir,
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )
    os.proc("git", "config", "user.email", "actions@github.com").call(
      cwd = repoDir,
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )

    val releaseUrl =
      s"https://github.com/${GitHubReleaseAssets.ghOrg}/${GitHubReleaseAssets.ghName}" +
        s"/releases/download/v$version"
    val jarUrl             = s"$releaseUrl/coursier"
    val launcherX86_64Url  = s"$releaseUrl/cs-x86_64-apple-darwin.gz"
    val launcherAarch64Url = s"$releaseUrl/cs-aarch64-apple-darwin.gz"

    def download(url: String, name: String): os.Path = {
      val dest = dlDir / name
      System.err.println(s"Getting $url")
      os.proc("curl", "-fsSLo", dest.toString, url).call(
        stdin = os.Inherit,
        stdout = os.Inherit,
        stderr = os.Inherit
      )
      dest
    }

    val jarSha256             = sha256(download(jarUrl, "jar-launcher"))
    val launcherX86_64Sha256  = sha256(download(launcherX86_64Url, "launcher-x86_64"))
    val launcherAarch64Sha256 = sha256(download(launcherAarch64Url, "launcher-aarch64"))

    val content = os.read(template)
      .replace("@LAUNCHER_VERSION@", version)
      .replace("@LAUNCHER_X86_64_URL@", launcherX86_64Url)
      .replace("@LAUNCHER_X86_64_SHA256@", launcherX86_64Sha256)
      .replace("@LAUNCHER_AARCH64_URL@", launcherAarch64Url)
      .replace("@LAUNCHER_AARCH64_SHA256@", launcherAarch64Sha256)
      .replace("@JAR_LAUNCHER_URL@", jarUrl)
      .replace("@JAR_LAUNCHER_SHA256@", jarSha256)

    val dest = os.rel / "coursier.rb"
    os.write.over(repoDir / dest, content)

    os.proc("git", "add", "--", dest).call(
      cwd = repoDir,
      stdin = os.Inherit,
      stdout = os.Inherit,
      stderr = os.Inherit
    )

    if (gitRepoHasChanges(repoDir)) {
      os.proc("git", "commit", "-m", s"Updates for $version").call(
        cwd = repoDir,
        stdin = os.Inherit,
        stdout = os.Inherit,
        stderr = os.Inherit
      )

      if (dryRun)
        System.err.println("Dry run, not pushing changes")
      else {
        System.err.println("Pushing changes")
        os.proc("git", "push", "origin", branch).call(
          cwd = repoDir,
          stdin = os.Inherit,
          stdout = os.Inherit,
          stderr = os.Inherit
        )
      }
    }
    else
      System.err.println("Nothing changed")
  }
}
