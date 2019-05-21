package coursier.cli.install

import caseapp.core.RemainingArgs
import caseapp.core.app.CaseApp

object InstallPath extends CaseApp[InstallPathOptions] {
  def run(options: InstallPathOptions, args: RemainingArgs): Unit =
    println(InstallParams.defaultDir.toString)
}
