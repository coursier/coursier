//> using dep com.lihaoyi::os-lib:0.11.4

import java.security.MessageDigest

object UpdateBrewFormula {
  def gitUsername = "Github Actions"
  def gitEmail    = "actions@github.com"

  val workDir = os.pwd / "target"

  val templateFile = os.pwd / ".github" / "scripts" / "coursier.rb.template"

  def main(args: Array[String]): Unit = {

    val dryRun = args match {
      case Array()                   => false
      case Array("-n" | "--dry-run") => true
      case _                         =>
        System.err.println(s"Usage: UpdateBrewFormula (-n|--dry-run)?")
        sys.exit(1)
    }

    val version =
      Option(System.getenv("GITHUB_REF")) match {
        case None => sys.error(s"GITHUB_REF not set, could not get current tag")
        case Some(tag) if tag.startsWith("refs/tags/v") =>
          tag.stripPrefix("refs/tags/v")
        case Some(other) =>
          sys.error(s"GITHUB_REF $other not starting with refs/tags/v")
      }

    val repoDir = workDir / "homebrew-formulas"

    if (os.exists(repoDir)) {
      System.err.println(s"Cleaning up former clone at $repoDir")
      os.remove.all(repoDir)
    }

    os.makeDir.all(workDir)

    lazy val ghToken = Option(System.getenv("GH_TOKEN")).getOrElse {
      sys.error(s"GH_TOKEN not set")
    }

    val repo =
      if (dryRun) "https://github.com/coursier/homebrew-formulas.git"
      else s"https://$ghToken@github.com/coursier/homebrew-formulas.git"

    os.proc("git", "clone", repo, "-q", "-b", "master", "homebrew-formulas")
      .call(cwd = workDir, stdout = os.Inherit)

    os.proc("git", "config", "user.name", gitUsername)
      .call(cwd = repoDir, stdout = os.Inherit)
    os.proc("git", "config", "user.email", gitEmail)
      .call(cwd = repoDir, stdout = os.Inherit)

    val jarUrl = s"https://github.com/coursier/coursier/releases/download/v$version/coursier"
    val launcherX86_64Url =
      s"https://github.com/coursier/coursier/releases/download/v$version/cs-x86_64-apple-darwin.gz"
    val launcherAarch64Url =
      s"https://github.com/coursier/coursier/releases/download/v$version/cs-aarch64-apple-darwin.gz"

    val jarPath             = repoDir / "jar-launcher"
    val launcherX86_64Path  = repoDir / "launcher-x86_64"
    val launcherAarch64Path = repoDir / "launcher-aarch64"
    System.err.println(s"Getting $jarUrl")
    os.proc("curl", "-fLo", jarPath, jarUrl)
      .call(cwd = repoDir, stdout = os.Inherit)
    System.err.println(s"Getting $launcherX86_64Url")
    os.proc("curl", "-fLo", launcherX86_64Path, launcherX86_64Url)
      .call(cwd = repoDir, stdout = os.Inherit)
    System.err.println(s"Getting $launcherAarch64Url")
    os.proc("curl", "-fLo", launcherAarch64Path, launcherAarch64Url)
      .call(cwd = repoDir, stdout = os.Inherit)

    def sha256(path: os.Path): String = {
      val digest    = MessageDigest.getInstance("SHA-256")
      val bytes     = digest.digest(os.read.bytes(path))
      val hexString = new StringBuilder(2 * bytes.length)
      for (b <- bytes)
        hexString.append(String.format("%02x", b & 0xff))
      hexString.toString
    }
    val jarSha256             = sha256(jarPath)
    val launcherX86_64Sha256  = sha256(launcherX86_64Path)
    val launcherAarch64Sha256 = sha256(launcherAarch64Path)

    assert(jarSha256.nonEmpty)
    assert(launcherX86_64Sha256.nonEmpty)
    assert(launcherAarch64Sha256.nonEmpty)

    os.remove(jarPath)
    os.remove(launcherX86_64Path)
    os.remove(launcherAarch64Path)

    val template = os.read(templateFile)

    val content = template
      .replace("@LAUNCHER_VERSION@", version)
      .replace("@LAUNCHER_X86_64_URL@", launcherX86_64Url)
      .replace("@LAUNCHER_X86_64_SHA256@", launcherX86_64Sha256)
      .replace("@LAUNCHER_AARCH64_URL@", launcherAarch64Url)
      .replace("@LAUNCHER_AARCH64_SHA256@", launcherAarch64Sha256)
      .replace("@JAR_LAUNCHER_URL@", jarUrl)
      .replace("@JAR_LAUNCHER_SHA256@", jarSha256)

    val dest = os.rel / "coursier.rb"
    os.write.over(repoDir / dest, content)

    os.proc("git", "add", "--", dest)
      .call(cwd = repoDir, stdout = os.Inherit)

    val gitStatusOutput = os.proc("git", "status")
      .call(cwd = repoDir, stderr = os.Pipe, mergeErrIntoOut = true)
      .out
      .text()

    if (gitStatusOutput.contains("nothing to commit"))
      println("Nothing changed")
    else {
      os.proc("git", "commit", "-m", s"Updates for $version")
        .call(cwd = repoDir, stdout = os.Inherit)

      if (dryRun)
        println("Dry run, not pushing changes")
      else {
        println("Pushing changes")
        os.proc("git", "push", "origin", "master")
          .call(cwd = repoDir, stdout = os.Inherit)
      }
    }
  }
}
