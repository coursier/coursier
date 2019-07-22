package coursier.cache.internal

import java.io.Writer

import org.fusesource.jansi.Ansi.{Erase, ansi}
import org.jline.terminal.{Terminal => JLineTerminal, TerminalBuilder => JLineTerminalBuilder}

object Terminal {

  private def withTerm[T](f: JLineTerminal => T): T = {
    var term: JLineTerminal = null
    try {
      term = JLineTerminalBuilder.terminal()
      f(term)
    } finally {
      if (term != null)
        term.close()
    }
  }

  def consoleDims(): (Int, Int) =
    withTerm(t => (t.getWidth, t.getHeight - 1))

  implicit class Ansi(val output: Writer) extends AnyVal {
    /**
      * Move up `n` squares
      */
    def up(n: Int): Unit = if (n > 0) output.write(ansi().cursorUp(n).toString)
    /**
      * Move down `n` squares
      */
    def down(n: Int): Unit = if (n > 0) output.write(ansi().cursorDown(n).toString)
    /**
      * Move left `n` squares
      */
    def left(n: Int): Unit = if (n > 0) output.write(ansi().cursorLeft(n).toString)

    /**
      * Clear the current line
      *
      * n=0: clear from cursor to end of line
      * n=1: clear from cursor to start of line
      * n=2: clear entire line
      */
    def clearLine(n: Int): Unit = {
      val erase = n match {
        case 0 => Erase.FORWARD
        case 1 => Erase.BACKWARD
        case _ => Erase.ALL
      }
      output.write(ansi().eraseLine(erase).toString)
    }
  }

}
