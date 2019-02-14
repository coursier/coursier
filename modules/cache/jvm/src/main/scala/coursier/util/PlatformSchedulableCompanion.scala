package coursier.util
import java.util.concurrent.{ExecutorService, Executors, ThreadFactory}

abstract class PlatformSchedulableCompanion {

  lazy val defaultThreadPool =
    fixedThreadPool(4 max Runtime.getRuntime.availableProcessors())

  def fixedThreadPool(size: Int): ExecutorService =
    Executors.newFixedThreadPool(
      size,
      // from scalaz.concurrent.Strategy.DefaultDaemonThreadFactory
      new ThreadFactory {
        val defaultThreadFactory = Executors.defaultThreadFactory()
        def newThread(r: Runnable) = {
          val t = defaultThreadFactory.newThread(r)
          t.setDaemon(true)
          t
        }
      }
    )

}
