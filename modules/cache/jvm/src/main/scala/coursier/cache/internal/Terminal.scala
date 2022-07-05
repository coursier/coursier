package coursier.cache.internal

import java.io.{File, Writer}

import scala.util.{Properties, Try}

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
        def buffer[T](f: => T): T   = f
      }
      Try(Process(Seq("bash", "-c", s"$pathedTput $s 2> /dev/tty")).!!(nullLog).trim.toInt).toOption
    }
    else
      None

  @deprecated("Should be removed at some point in future releases", "2.0.0-RC3")
  def consoleDimOrThrow(s: String): Int =
    if (ttyAvailable0) {
      import sys.process._
      val nullLog = new ProcessLogger {
        def out(s: => String): Unit = {}
        def err(s: => String): Unit = {}
        def buffer[T](f: => T): T   = f
      }
      Process(Seq("bash", "-c", s"$pathedTput $s 2> /dev/tty")).!!(nullLog).trim.toInt
    }
    else
      throw new Exception("TTY not available")

  private def consoleDimsFromTty(): Option[(Int, Int)] =
    if (ttyAvailable0) {
      import sys.process._
      val nullLog = new ProcessLogger {
        def out(s: => String): Unit = {}
        def err(s: => String): Unit = {}
        def buffer[T](f: => T): T   = f
      }
      def valueOpt(s: String) =
        Try(Process(Seq("bash", "-c", s"$pathedTput $s 2> /dev/tty")).!!(nullLog).trim.toInt)
          .toOption

      for {
        width  <- valueOpt("cols")
        height <- valueOpt("lines")
      } yield (width, height)
    }
    else
      None

  private def fromJLine(): Option[(Int, Int)] =
    if (Properties.isWin)
      Some {
        if (coursier.paths.Util.useJni()) {
          val size = coursier.jniutils.WindowsAnsiTerminal.terminalSize()
          (size.getWidth, size.getHeight)
        }
        else {
          val size = io.github.alexarchambault.windowsansi.WindowsAnsi.terminalSize()
          (size.getWidth, size.getHeight)
        }
      }
    else
      None

  def consoleDims(): (Int, Int) =
    consoleDimsFromTty()
      .orElse(fromJLine())
      .getOrElse {
        // throw instead?
        (80, 25)
      }

  implicit class Ansi(val output: Writer) extends AnyVal {
    private def control(n: Int, c: Char): Unit =
      output.write("\u001b[" + n + c)

    /* Move up `n` squares */
    def up(n: Int): Unit = if (n > 0) control(n, 'A')

    /* Move down `n` squares */
    def down(n: Int): Unit = if (n > 0) control(n, 'B')

    /* Move left `n` squares */
    def left(n: Int): Unit = if (n > 0) control(n, 'D')

    /*
     * Clear the current line
     *
     * n=0: clear from cursor to end of line n=1: clear from cursor to start of line n=2: clear
     * entire line
     */
    def clearLine(n: Int): Unit =
      control(n, 'K')
  }

}
