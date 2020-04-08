package coursier.cache.internal

final class ConsoleDim {

  @volatile private var dimsOpt: Option[(Int, Int)] = None
  private var initialized = false
  private val lock = new Object

  // FIXME On Windows, don't cache Terminal.consoleDims()? (should be a - cheap I think - native call)

  private def setup(): Unit = {

    try {
      SigWinch.addHandler(
        new Runnable {
          def run(): Unit =
            lock.synchronized {
              dimsOpt = None
            }
        }
      )
    } catch {
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
          val dims = Terminal.consoleDims()
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
