package coursierbuild

import java.io.File

import scala.util.matching.Regex
import scala.util.{Properties, Try}

/** Helpers around the standalone `cs.sh` launcher script, at the root of this repository */
object CsSh extends VersionPin {

  def relPath      = os.sub / "cs.sh"
  def what         = "CS_VERSION"
  def branchPrefix = "update-cs-sh"
  def taskName     = "updateCsShVersion"

  def title(newVersion: String) =
    s"Update cs.sh to $newVersion"

  protected def versionRegex: Regex =
    """(?m)^(CS_VERSION=)"([^"]*)"$""".r

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
}
