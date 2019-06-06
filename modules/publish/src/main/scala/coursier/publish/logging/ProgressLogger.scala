package coursier.publish.logging

import java.io.Writer
import java.lang.{Boolean => JBoolean}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{ConcurrentHashMap, Executors, ScheduledFuture, TimeUnit}

import com.lightbend.emoji.ShortCodes.Defaults.defaultImplicit.emoji
import coursier.cache.internal.Terminal.Ansi
import coursier.cache.internal.ThreadUtil

import scala.collection.JavaConverters._

/**
  * Displays the progress of some task on a single line.
  *
  * With a ticker, an emoji once it's done, a summary of how many sub-tasks are done, and the total
  * number of sub-tasks if it is known.
  */
final class ProgressLogger[T](
  processedMessage: String,
  elementName: String,
  out: Writer,
  updateOnChange: Boolean = false,
  doneEmoji: Option[String] = emoji("heavy_check_mark").map(Console.GREEN + _ + Console.RESET)
) {

  import ProgressLogger._

  private val states = new ConcurrentHashMap[T, State]
  private var printed = 0

  private def clear(): Unit = {

    for (_ <- 1 to printed) {
      out.clearLine(2)
      out.down(1)
    }

    out.up(printed)
    out.flush()

    printed = 0
  }

  // from https://github.com/mitsuhiko/indicatif/blob/0c3c0b5cbe666402ed7d3b9366c346f6eb0228fe/src/progress.rs#L202
  private[this] val tickers = "⠁⠁⠉⠙⠚⠒⠂⠂⠒⠲⠴⠤⠄⠄⠤⠠⠠⠤⠦⠖⠒⠐⠐⠒⠓⠋⠉⠈⠈ "

  private def update(scrollUp: Boolean = true): Runnable =
    new Runnable {
      def run() = {

        clear()

        for ((_, s) <- states.asScala.toVector.sortBy(_._2.totalOpt.sum)) {
          val m = s.processed.asScala.iterator.toMap
          val ongoing = m.count(_._2.isLeft)
          val extra =
            if (ongoing > 0) {
              val total = m.iterator.flatMap(_._2.left.toOption.iterator.map(_._2)).sum
              if (total > 0L) {
                val done = m.iterator.flatMap(_._2.left.toOption.iterator.filter(_._2 > 0L).map(_._1)).sum
                val pct = f"${100L * done.toDouble / total}%.2f %%"
                s" ($pct of $ongoing on-going)"
              } else
                s" ($ongoing on-going)"
            } else
              ""
          val doneCount = m.count(_._2.isRight)
          val done = s.done.get()
          val em =
            if (done)
              doneEmoji.fold("")(_ + " ")
            else
              tickers(doneCount % tickers.length) + " "
          out.write(s" $em$processedMessage $doneCount${s.totalOpt.filter(_ => !done).fold("")(t => s" / $t")} $elementName$extra\n")
          printed += 1
        }

        if (scrollUp)
          out.up(printed)

        out.flush()
      }
    }

  private val onChangeUpdate = update()
  private val onChangeUpdateLock = new Object
  private def onChange(): Unit = {
    if (updateOnChange)
      onChangeUpdateLock.synchronized {
        onChangeUpdate.run()
      }
  }

  def processingSet(id: T, totalOpt: Option[Int]): Unit = {
    val s = new State(totalOpt)
    val previous = states.putIfAbsent(id, s)
    assert(previous eq null)
    onChange()
  }
  def processedSet(id: T): Unit = {
    val s = states.get(id)
    assert(s ne null, s"Found ${states.asScala.iterator.map(_._1).toList}, not $id")
    val previous = s.done.getAndSet(true)
    assert(!previous)
    onChange()
  }

  def processing(url: String, id: T): Unit = {
    val s = states.get(id)
    assert(s ne null, s"$id not started")
    val previous = s.processed.putIfAbsent(url, Left((0, 0)))
    assert(previous eq null)
    onChange()
  }
  def progress(url: String, id: T, done: Long, total: Long): Unit = {
    val s = states.get(id)
    assert(s ne null, s"Found ${states.asScala.iterator.map(_._1).toList}, not $id")
    val b = s.processed.put(url, Left((done, total)))
    assert(b.isLeft)
    onChange()
  }
  def processed(url: String, id: T, errored: Boolean): Unit = {
    val s = states.get(id)
    assert(s ne null, s"Found ${states.asScala.iterator.map(_._1).toList}, not $id")
    val b = s.processed.put(url, Right(()))
    assert(b.isLeft)
    onChange()
  }

  // FIXME Unused if updateOnChange is true
  private val pool = Executors.newScheduledThreadPool(1, ThreadUtil.daemonThreadFactory())
  private var updateFutureOpt = Option.empty[ScheduledFuture[_]]

  private val period = 1000L / 50L

  def start(): Unit = {
    assert(!pool.isShutdown)
    assert(updateFutureOpt.isEmpty)
    if (!updateOnChange) {
      val f = pool.scheduleAtFixedRate(update(), 0L, period, TimeUnit.MILLISECONDS)
      updateFutureOpt = Some(f)
    }
  }
  def stop(keep: Boolean): Unit = {
    updateFutureOpt.foreach(_.cancel(false))
    pool.shutdown()
    pool.awaitTermination(2L * period, TimeUnit.MILLISECONDS)
    if (keep)
      update(scrollUp = false).run()
    else
      clear()
  }
}

object ProgressLogger {

  private final class State(val totalOpt: Option[Int]) {
    val done = new AtomicBoolean(false)
    val processed = new ConcurrentHashMap[String, Either[(Long, Long), Unit]]
  }

}
