package coursier.cli.install

import caseapp.core.RemainingArgs
import caseapp.core.app.CaseApp
import coursier.cli.util.Guard

object InstallPath extends CaseApp[InstallPathOptions] {
  def run(options: InstallPathOptions, args: RemainingArgs): Unit = {
    Guard()
    println(SharedInstallParams.defaultDir.toString)
  }
}
