package coursier.publish.logging

import java.io._
import java.util.concurrent.{Executors, ScheduledFuture, TimeUnit}

import coursier.cache.internal.Terminal.Ansi
import coursier.cache.internal.{ConsoleDim, ThreadUtil}

import scala.util.control.NonFatal

/**
  * Displays the last `bufferSize` lines of `is` in the terminal via `out`, updating them along time.
  */
final class OutputFrame(
  is: InputStream,
  out: Writer,
  bufferSize: Int,
  preamble: Seq[String],
  postamble: Seq[String]
) {

  private[this] val lines = new OutputFrame.Lines(bufferSize)
  private[this] val inCaseOfErrorLines = new OutputFrame.Lines(100) // don't hard-code size?

  private val readThread: Thread =
    new Thread("output-frame-read") {
      setDaemon(true)
      override def run() =
        try {
          val br = new BufferedReader(new InputStreamReader(is))
          var l: String = null
          while ({
            l = br.readLine()
            l != null
          }) {
            lines.append(l)
            inCaseOfErrorLines.append(l)
          }
        } catch {
          case _: InterruptedException =>
            // normal exit
          case NonFatal(e) =>
            System.err.println(s"Caught exception in output-frame-read")
            e.printStackTrace(System.err)
        }
    }

  private def clear(): Unit = {

    var count = 0

    for (_ <- preamble) {
      out.clearLine(2)
      out.write('\n')
      count += 1
    }

    var n = 0
    while (n < bufferSize + 1) {
      out.clearLine(2)
      out.write('\n')
      n += 1
    }

    count += n

    for (_ <- postamble) {
      out.clearLine(2)
      out.write('\n')
      count += 1
    }

    out.up(count)

    out.flush()
  }

  private def updateOutput(scrollUp: Boolean = true): Runnable =
    new Runnable {
      def run() = {
        val width = ConsoleDim.width()

        var count = 0

        for (l <- preamble) {
          val l0 =
            if (preamble.length <= width) l
            else l.substring(0, width)
          out.clearLine(2)
          out.write(l0 + "\n")
          count += 1
        }

        val it = lines.linesIterator()
        var n = 0
        while (n < bufferSize && it.hasNext) {
          val l = it.next()
            // https://stackoverflow.com/a/25189932/3714539
            .replaceAll("\u001B\\[[\\d;]*[^\\d;]","")
          val l0 =
            if (l.length <= width) l
            else l.substring(0, width)
          out.clearLine(2)
          out.write(l0 + "\n")
          n += 1
        }

        while (n < bufferSize + 1) {
          out.clearLine(2)
          out.write('\n')
          n += 1
        }

        count += n

        for (l <- postamble) {
          val l0 =
            if (preamble.length <= width) l
            else l.substring(0, width)
          out.clearLine(2)
          out.write(l0 + "\n")
          count += 1
        }

        if (scrollUp)
          out.up(count)

        out.flush()
      }
    }

  private val pool = Executors.newScheduledThreadPool(1, ThreadUtil.daemonThreadFactory())
  private var updateFutureOpt = Option.empty[ScheduledFuture[_]]

  private val period = 1000L / 50L

  def start(): Unit = {
    assert(!pool.isShutdown)
    assert(!readThread.isAlive)
    assert(updateFutureOpt.isEmpty)
    readThread.start()
    val f = pool.scheduleAtFixedRate(updateOutput(), 0L, period, TimeUnit.MILLISECONDS)
    updateFutureOpt = Some(f)
  }

  def stop(keepFrame: Boolean = true, errored: Option[PrintStream] = None): Unit = {
    readThread.interrupt()
    updateFutureOpt.foreach(_.cancel(false))
    pool.shutdown()
    pool.awaitTermination(2L * period, TimeUnit.MILLISECONDS)
    errored match {
      case None =>
        if (keepFrame)
          updateOutput(scrollUp = false).run()
        else
          clear()
      case Some(errStream) =>
        inCaseOfErrorLines.linesIterator().foreach(errStream.println)
    }
  }

}

object OutputFrame {

  private final class Lines(bufferSize: Int) {

    @volatile private[this] var first: Line = _
    @volatile private[this] var last: Line = _


    // should not be called multiple times in parallel
    def append(value: String): Unit = {
      assert(value ne null)
      val index = if (last eq null) 0L else last.index + 1L
      val l = new Line(value, index)

      if (last eq null) {
        assert(first eq null)
        first = l
        last = l
      } else {
        last.setNext(l)
        last = l
      }

      while (last.index - first.index > bufferSize) {
        // Let the former `first` be garbage-collected, if / once no more `linesIterator` reference it.
        first = first.next
        assert(first ne null)
      }
    }

    // thread safe, and can be used while append gets called
    def linesIterator(): Iterator[String] =
      new Iterator[String] {
        var current = first
        def hasNext = current ne null
        def next() = {
          val v = current.value
          current = current.next
          v
        }
      }

  }

  private final class Line(val value: String, val index: Long) {

    @volatile private[this] var next0: Line = _

    def setNext(next: Line): Unit = {
      assert(this.next0 eq null)
      assert(!(next eq null))
      this.next0 = next
    }

    def next: Line =
      next0
  }

}
