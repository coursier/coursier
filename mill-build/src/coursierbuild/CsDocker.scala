package coursierbuild

import scala.util.matching.Regex

/** The coursier version whose Linux launcher is downloaded in the docker images building our Linux
  * launchers - `Versions.csDocker`, passed to the native image builds as
  * `Launchers.linuxCsLauncher`
  */
object CsDocker extends VersionPin {

  def relPath      = os.sub / "mill-build/src/coursierbuild/Versions.scala"
  def what         = "csDocker"
  def branchPrefix = "update-cs-docker"
  def taskName     = "updateCsDockerVersion"

  def title(newVersion: String) =
    s"Update the cs version building the Linux launchers to $newVersion"

  protected def versionRegex: Regex =
    """(?m)^(\s*def csDocker\s*=\s*)"([^"]*)"$""".r
}
