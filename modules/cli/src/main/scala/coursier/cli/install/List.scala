package coursier.cli.install

import caseapp.core.app.Command
import caseapp.core.RemainingArgs
import coursier.install.InstallDir

object List extends Command[ListOptions] {
  def run(options: ListOptions, args: RemainingArgs): Unit = {
    val params     = ListParams(options)
    val installDir = InstallDir(params.installPath, new NoopCache)
    val names      = installDir.list()
    print(names.map(_ + System.lineSeparator).mkString)
  }
}
