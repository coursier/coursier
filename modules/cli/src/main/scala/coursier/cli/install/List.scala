package coursier.cli.install

import caseapp.core.app.CaseApp
import caseapp.core.RemainingArgs
import coursier.install.Updatable

object List extends CaseApp[ListOptions] {
  def run(options: ListOptions, args: RemainingArgs): Unit = {
    val params = ListParams(options)
    val names = Updatable.list(params.installPath)
    System.err.println(names.mkString(" "))
  }
}
