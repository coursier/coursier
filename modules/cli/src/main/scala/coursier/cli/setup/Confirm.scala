package coursier.cli.setup

import java.util.{Locale, Scanner}

import coursier.util.Task

import scala.annotation.tailrec
import java.io.InputStream
import java.io.PrintStream

trait Confirm {
  def confirm(message: String, default: Boolean): Task[Boolean]
}

object Confirm {

  final class ConsoleInput(
    in: InputStream,
    out: PrintStream,
    locale: Locale
  ) extends Confirm {
    def confirm(message: String, default: Boolean): Task[Boolean] =
      Task.delay {

        val choice =
          if (default) "[Y/n]"
          else "[y/N]"

        out.print(s"$message $choice ")

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

  final class YesToAll(out: PrintStream) extends Confirm {
    def confirm(message: String, default: Boolean): Task[Boolean] =
      Task.delay {
        out.println(message + " [Y/n] Y")
        true
      }
  }

  def consoleInput(): Confirm =
    new ConsoleInput(System.in, System.out, Locale.getDefault)
  def consoleInput(in: InputStream, out: PrintStream): Confirm =
    new ConsoleInput(in, out, Locale.getDefault)
  def consoleInput(in: InputStream, out: PrintStream, locale: Locale): Confirm =
    new ConsoleInput(in, out, locale)

  def yesToAll(): Confirm =
    new YesToAll(System.out)
  def yesToAll(out: PrintStream): Confirm =
    new YesToAll(out)

  def default: Confirm =
    consoleInput()

}
