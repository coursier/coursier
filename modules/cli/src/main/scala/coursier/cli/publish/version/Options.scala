package coursier.cli.publish.version

import caseapp._

final case class Options(
  @HelpMessage("Check if the current version is a snapshot one")
    isSnapshot: Boolean = false,
  @Name("q") // hmm, doesn't work
    quiet: Boolean = false
)

object Options {
  implicit val parser = Parser[Options]
  implicit val help = caseapp.core.help.Help[Options]
}
