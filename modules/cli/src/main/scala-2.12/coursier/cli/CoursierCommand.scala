package coursier.cli

import caseapp.CommandParser
import caseapp.core.help.CommandsHelp
import coursier.cli.complete.Complete
import coursier.cli.fetch.Fetch
import coursier.cli.launch.Launch
import coursier.cli.resolve.Resolve

object CoursierCommand {

  val parser =
    CommandParser.nil
      .add(Bootstrap)
      .add(Complete)
      .add(Fetch)
      .add(Launch)
      .add(Resolve)
      .add(SparkSubmit)
      .reverse

  val help =
    CommandsHelp.nil
      .add(Bootstrap)
      .add(Complete)
      .add(Fetch)
      .add(Launch)
      .add(Resolve)
      .add(SparkSubmit)
      .reverse

}
