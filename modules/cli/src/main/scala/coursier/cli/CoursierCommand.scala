package coursier.cli

import caseapp.CommandParser
import caseapp.core.help.CommandsHelp
import coursier.cli.bootstrap.Bootstrap
import coursier.cli.complete.Complete
import coursier.cli.fetch.Fetch
import coursier.cli.install.{Install, Uninstall, Update}
import coursier.cli.jvm.{Java, JavaHome}
import coursier.cli.launch.Launch
import coursier.cli.publish.Publish
import coursier.cli.resolve.Resolve
import coursier.cli.setup.Setup

object CoursierCommand {

  val parser =
    CommandParser.nil
      .add(Bootstrap)
      .add(Complete)
      .add(Fetch)
      .add(Install)
      .add(Java)
      .add(JavaHome)
      .add(Launch)
      .add(Publish)
      .add(Resolve)
      .add(Setup)
      .add(Uninstall)
      .add(Update)
      .reverse

  val help =
    CommandsHelp.nil
      .add(Bootstrap)
      .add(Complete)
      .add(Fetch)
      .add(Install)
      .add(Java)
      .add(JavaHome)
      .add(Launch)
      .add(Publish)
      .add(Resolve)
      .add(Setup)
      .add(Uninstall)
      .add(Update)
      .reverse

}
