package coursier.cli

import caseapp._

abstract class CoursierCommand[T](implicit parser: Parser[T], help: Help[T])
    extends Command[T]()(parser, help) {

  override def hasFullHelp = true
}
