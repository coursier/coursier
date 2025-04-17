package coursier.cache.internal

import java.util.concurrent.{
  ExecutorService,
  LinkedBlockingQueue,
  ScheduledExecutorService,
  ScheduledThreadPoolExecutor,
  ThreadFactory,
  ThreadPoolExecutor,
  TimeUnit
}
import java.util.concurrent.atomic.AtomicInteger

object ThreadUtil {

  private val poolNumber = new AtomicInteger(1)

  def daemonThreadFactory(): ThreadFactory =
    daemonThreadFactory(name = "")

  def daemonThreadFactory(name: String): ThreadFactory = {

    val name0 =
      if (name.isEmpty) {
        val poolNumber0 = poolNumber.getAndIncrement()
        s"coursier-pool-$poolNumber0"
      }
      else
        name

    val threadNumber = new AtomicInteger(1)

    new ThreadFactory {
      def newThread(r: Runnable) = {
        val threadNumber0 = threadNumber.getAndIncrement()
        val t             = new Thread(r, s"$name0-thread-$threadNumber0")
        t.setDaemon(true)
        t.setPriority(Thread.NORM_PRIORITY)
        t
      }
    }
  }

  def fixedThreadPool(size: Int): ExecutorService =
    fixedThreadPool(size, name = "")

  def fixedThreadPool(size: Int, name: String): ExecutorService = {

    val factory = daemonThreadFactory(name)

    // 1 min keep alive, so that threads get stopped a bit after resolution / downloading is done
    val executor = new ThreadPoolExecutor(
      size,
      size,
      1L,
      TimeUnit.MINUTES,
      new LinkedBlockingQueue[Runnable],
      factory
    )
    executor.allowCoreThreadTimeOut(true)
    executor
  }

  def fixedScheduledThreadPool(size: Int): ScheduledExecutorService =
    fixedScheduledThreadPool(size, name = "")

  def fixedScheduledThreadPool(size: Int, name: String): ScheduledExecutorService = {

    val factory = daemonThreadFactory(name)

    val executor = new ScheduledThreadPoolExecutor(size, factory)
    executor.setKeepAliveTime(1L, TimeUnit.MINUTES)
    executor.allowCoreThreadTimeOut(true)
    executor
  }

  def withFixedThreadPool[T](size: Int)(f: ExecutorService => T): T = {

    var pool: ExecutorService = null
    try {
      pool = fixedThreadPool(size)
      f(pool)
    }
    finally if (pool != null)
        pool.shutdown()
  }

}
