package coursier
package cli

import caseapp._
import coursier.cli.options.CommonOptions

object Resolve extends CaseApp[CommonOptions] {

  def run(options: CommonOptions, args: RemainingArgs): Unit = {
    new Helper(options, args.all, printResultStdout = true)
  }

}
