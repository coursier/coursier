package coursier.cache.internal

import java.io.{File, Writer}

import org.fusesource.jansi.Ansi.{Erase, ansi}
import org.jline.terminal.{Terminal => JLineTerminal, TerminalBuilder => JLineTerminalBuilder}

import scala.util.Try

object Terminal {

  // A few things were cut-n-pasted and adapted from
  // https://github.com/lihaoyi/Ammonite/blob/10854e3b8b454a74198058ba258734a17af32023/terminal/src/main/scala/ammonite/terminal/Utils.scala

  private lazy val pathedTput = if (new File("/usr/bin/tput").exists()) "/usr/bin/tput" else "tput"

  private lazy val ttyAvailable0: Boolean =
    new File("/dev/tty").exists()

  @deprecated("Should be made private at some point in future releases", "2.0.0-RC3")
  lazy val ttyAvailable: Boolean =
    ttyAvailable0

  @deprecated("Should be removed at some point in future releases", "2.0.0-RC3")
  def consoleDim(s: String): Option[Int] =
    if (ttyAvailable0) {
      import sys.process._
      val nullLog = new ProcessLogger {
        def out(s: => String): Unit = {}
        def err(s: => String): Unit = {}
        def buffer[T](f: => T): T = f
      }
      Try(Process(Seq("bash", "-c", s"$pathedTput $s 2> /dev/tty")).!!(nullLog).trim.toInt).toOption
    } else
      None

  @deprecated("Should be removed at some point in future releases", "2.0.0-RC3")
  def consoleDimOrThrow(s: String): Int =
    if (ttyAvailable0) {
      import sys.process._
      val nullLog = new ProcessLogger {
        def out(s: => String): Unit = {}
        def err(s: => String): Unit = {}
        def buffer[T](f: => T): T = f
      }
      Process(Seq("bash", "-c", s"$pathedTput $s 2> /dev/tty")).!!(nullLog).trim.toInt
    } else
      throw new Exception("TTY not available")

  private def consoleDimsFromTty(): Option[(Int, Int)] =
    if (ttyAvailable0) {
      import sys.process._
      val nullLog = new ProcessLogger {
        def out(s: => String): Unit = {}
        def err(s: => String): Unit = {}
        def buffer[T](f: => T): T = f
      }
      def valueOpt(s: String) =
        Try(Process(Seq("bash", "-c", s"$pathedTput $s 2> /dev/tty")).!!(nullLog).trim.toInt).toOption

      for {
        width <- valueOpt("cols")
        height <- valueOpt("lines")
      } yield (width, height)
    } else
      None

  private def fromJLine(): (Int, Int) = {
    var term: JLineTerminal = null
    try {
      term = JLineTerminalBuilder.terminal()
      (term.getWidth, term.getHeight)
    } finally {
      if (term != null)
        term.close()
    }
  }

  def consoleDims(): (Int, Int) =
    consoleDimsFromTty()
      .getOrElse(fromJLine())

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
