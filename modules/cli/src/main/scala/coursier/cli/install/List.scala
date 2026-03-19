package coursier.cli.install

import caseapp.core.RemainingArgs
import coursier.cli.{CoursierCommand, CommandGroup}
import coursier.install.InstallDir

object List extends CoursierCommand[ListOptions] {

  override def group: String = CommandGroup.install

  def run(options: ListOptions, args: RemainingArgs): Unit = {
    val params     = ListParams(options)
    val installDir = InstallDir(params.installPath, new NoopCache)
    if (params.versions) {
      val entries = installDir.listWithVersions()
      print(entries.map {
        case (name, Some(version)) => name + " " + version + System.lineSeparator
        case (name, None)          => name + System.lineSeparator
      }.mkString)
    }
    else {
      val names = installDir.list()
      print(names.map(_ + System.lineSeparator).mkString)
    }
  }
}
