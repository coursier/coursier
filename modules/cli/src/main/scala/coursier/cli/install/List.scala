package coursier.cli.install

import caseapp.core.RemainingArgs
import coursier.cli.{CoursierCommand, CommandGroup}
import coursier.install.InstallDir

object List extends CoursierCommand[ListOptions] {

  override def group: String = CommandGroup.install

  def run(options: ListOptions, args: RemainingArgs): Unit = {
    val params     = ListParams(options)
    val installDir = InstallDir(params.installPath, new NoopCache)
    val names      = installDir.list()
    print(names.map(_ + System.lineSeparator).mkString)
  }
}
