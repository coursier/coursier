package coursier.cache.internal

import sun.misc.{Signal, SignalHandler}

final class ConsoleDim {

  @volatile private var dimsOpt: Option[(Int, Int)] = None
  private var initialized = false
  private val lock = new Object

  private def setup(): Unit = {

    // From https://stackoverflow.com/q/31594364/3714539

    val terminalSizeChangedHandler: SignalHandler =
      new SignalHandler {
        def handle(sig: Signal): Unit =
          lock.synchronized {
            dimsOpt = None
          }
      }

    try Signal.handle(new Signal("WINCH"), terminalSizeChangedHandler)
    catch {
      case _: IllegalArgumentException =>
        // ignored
    }

    initialized = true
  }

  private def dims(): (Int, Int) =
    dimsOpt.getOrElse {
      lock.synchronized {
        dimsOpt.getOrElse {
          if (!initialized)
            setup()
          val dims = (Terminal.consoleDimOrThrow("cols"), Terminal.consoleDimOrThrow("lines"))
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

  lazy val get: ConsoleDim =
    new ConsoleDim

  def width(): Int =
    get.width()
  def height(): Int =
    get.height()

}
