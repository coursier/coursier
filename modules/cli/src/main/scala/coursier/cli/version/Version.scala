package coursier.cli.version

import caseapp.core.RemainingArgs
import coursier.cli.CoursierCommand
import coursier.util.Properties

object Version extends CoursierCommand[VersionOptions] {
  def run(options: VersionOptions, args: RemainingArgs): Unit = {
    println(Properties.version)
  }
}
