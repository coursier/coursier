package coursier.cli.publish.logging

import sun.misc.{Signal, SignalHandler}

// from https://github.com/lihaoyi/Ammonite/blob/26b7ebcace16b4b5b4b68f9344ea6f6f48d9b53e/terminal/src/main/scala/ammonite/terminal/ConsoleDim.scala

final class ConsoleDim {

  @volatile private var dimsOpt: Option[(Int, Int)] = None
  private var initialized = false
  private val lock = new Object

  private def setup(): Unit = {

    // From https://stackoverflow.com/q/31594364/3714539

    val terminalSizeChangedHandler: SignalHandler =
      new SignalHandler {
        override def handle(sig: Signal): Unit =
          lock.synchronized {
            dimsOpt = None
          }
      }

    Signal.handle(new Signal("WINCH"), terminalSizeChangedHandler)

    initialized = true
  }

  private def dims(): (Int, Int) =
    dimsOpt.getOrElse {
      lock.synchronized {
        dimsOpt.getOrElse {
          if (!initialized)
            setup()
          val dims = (ConsoleDim.consoleDim("cols"), ConsoleDim.consoleDim("lines"))
          dimsOpt = Some(dims)
          dims
        }
      }
    }

  def width(): Int =
    dims()._1
  def height(): Int =
    dims()._2

}

object ConsoleDim {

  private val pathedTput = if (new java.io.File("/usr/bin/tput").exists()) "/usr/bin/tput" else "tput"
  private def consoleDim(s: String) = {
    import sys.process._
    Seq("sh", "-c", s"$pathedTput $s 2> /dev/tty").!!.trim.toInt
  }

  lazy val get: ConsoleDim =
    new ConsoleDim

  def width(): Int =
    get.width()
  def height(): Int =
    get.height()

}
