package coursier.cli.setup

import java.io.{InputStream, PrintStream}
import java.util.{Locale, Scanner}

import coursier.util.Task
import dataclass.data

import scala.annotation.tailrec

trait Confirm {
  def confirm(message: String, default: Boolean): Task[Boolean]
}

object Confirm {

  @data class ConsoleInput(
    in: InputStream = System.in,
    out: PrintStream = System.out,
    locale: Locale = Locale.getDefault,
    @since
    indent: Int = 0
  ) extends Confirm {
    private val marginOpt = if (indent > 0) Some(" " * indent) else None
    def confirm(message: String, default: Boolean): Task[Boolean] =
      Task.delay {

        val choice =
          if (default) "[Y/n]"
          else "[y/N]"

        val message0 = marginOpt match {
          case None => message
          case Some(margin) => message.linesIterator.map(margin + _).mkString(System.lineSeparator())
        }
        out.print(s"$message0 $choice ")

        @tailrec
        def loop(): Boolean = {
          val scanner = new Scanner(in)
          val resp = scanner.nextLine()
          val resp0 = resp
            .filter(!_.isSpaceChar)
            .toLowerCase(locale)
            .distinct

          resp0 match {
            case "y" => true
            case "n" => false
            case "" => default
            case _ =>
              out.print(s"Please answer Y or N. $choice ")
              loop()
          }
        }

        loop()
      }
  }

  @data class YesToAll(
    out: PrintStream = System.out
  ) extends Confirm {
    def confirm(message: String, default: Boolean): Task[Boolean] =
      Task.delay {
        out.println(message + " [Y/n] Y")
        true
      }
  }

  def default: Confirm =
    ConsoleInput()

}
