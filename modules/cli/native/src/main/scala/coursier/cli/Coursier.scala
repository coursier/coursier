package coursier.cli

import caseapp.core.app.CommandsEntryPoint
import caseapp.core.help.HelpFormat

/** The Scala Native entry point.
  *
  * Only `get` is wired up here: resolution needs the `core` and `coursier` modules, which are
  * JVM-only, and archives need `archive-cache`. This launcher is `cache` and nothing else.
  */
object Coursier extends CommandsEntryPoint {

  def progName = "cs"

  override def description =
    "Coursier is the Scala application and artifact manager. " +
      "This launcher can download and cache files from the web."

  val commands = Seq(
    get.Get
  )

  override def helpFormat: HelpFormat =
    HelpFormat.default()
      .copy(sortedCommandGroups = Some(CommandGroup.order))
}
