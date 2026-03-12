package coursier.cli

import caseapp._
import caseapp.core.Scala3Helpers._
import caseapp.core.help.HelpFormat
import coursier.cli.options.OptionGroup

import java.util.Locale

abstract class CoursierCommand[T](implicit parser: Parser[T], help: Help[T])
    extends Command[T]()(parser, help) {

  override def helpFormat: HelpFormat =
    HelpFormat.default().withSortedGroups(Some(OptionGroup.order))

  override def hasFullHelp = true

  lazy val experimentalFeatures: Boolean =
    Option(System.getenv("COURSIER_EXPERIMENTAL")).map(_.toLowerCase(Locale.ROOT)).exists {
      case "true" | "1" => true
      case _            => false
    }
}
