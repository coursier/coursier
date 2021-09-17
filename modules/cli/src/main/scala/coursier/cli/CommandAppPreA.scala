package coursier.cli

import caseapp.core.RemainingArgs
import caseapp.core.app.CommandAppWithPreCommand
import caseapp.core.commandparser.CommandParser
import caseapp.core.help.{CommandsHelp, Help}
import caseapp.core.parser.Parser
import shapeless.Coproduct
// TODO Move to case-app

/* The A suffix stands for anonymous */
abstract class CommandAppPreA[D, T <: Coproduct](
  beforeCommandParser: Parser[D],
  baseBeforeCommandMessages: Help[D],
  commandParser: CommandParser[T],
  commandsMessages: CommandsHelp[T]
  // format: off
) extends CommandAppWithPreCommand[D, T]()(
  beforeCommandParser,
  baseBeforeCommandMessages,
  commandParser,
  commandsMessages
) {
  // format: on

  def runA: T => RemainingArgs => Unit

  def run(options: T, remainingArgs: RemainingArgs): Unit =
    runA(options)(remainingArgs)

}
