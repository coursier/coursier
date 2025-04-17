package coursier.cache.loggers

import java.io.{OutputStream, OutputStreamWriter, Writer}
import java.util.concurrent.{ConcurrentHashMap, ConcurrentLinkedQueue, ScheduledExecutorService}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import coursier.cache.CacheLogger
import coursier.cache.internal.ThreadUtil
import coursier.cache.loggers.RefreshInfo.{CheckUpdateInfo, DownloadInfo}
import coursier.util.Artifact

import scala.collection.mutable.ArrayBuffer

object RefreshLogger {

  def defaultDisplay(
    fallbackMode: Boolean = defaultFallbackMode,
    quiet: Boolean = false
  ): RefreshDisplay =
    if (fallbackMode)
      new FallbackRefreshDisplay(quiet = quiet)
    else if (quiet)
      FileTypeRefreshDisplay.create()
    else
      ProgressBarRefreshDisplay.create()

  def create(): RefreshLogger =
    new RefreshLogger(new OutputStreamWriter(System.err), defaultDisplay())

  def create(os: OutputStream): RefreshLogger =
    new RefreshLogger(new OutputStreamWriter(os), defaultDisplay())

  def create(writer: OutputStreamWriter): RefreshLogger =
    new RefreshLogger(writer, defaultDisplay())

  def create(display: RefreshDisplay): RefreshLogger =
    new RefreshLogger(new OutputStreamWriter(System.err), display)

  def create(os: OutputStream, display: RefreshDisplay): RefreshLogger =
    new RefreshLogger(new OutputStreamWriter(os), display)

  def create(os: OutputStream, display: RefreshDisplay, logChanging: Boolean): RefreshLogger =
    new RefreshLogger(
      new OutputStreamWriter(os),
      display,
      fallbackMode = false,
      logChanging = logChanging
    )

  def create(
    os: OutputStream,
    display: RefreshDisplay,
    logChanging: Boolean,
    logPickedVersions: Boolean
  ): RefreshLogger =
    new RefreshLogger(
      new OutputStreamWriter(os),
      display,
      fallbackMode = false,
      logChanging = logChanging,
      logPickedVersions = logPickedVersions
    )

  def create(writer: OutputStreamWriter, display: RefreshDisplay): RefreshLogger =
    new RefreshLogger(writer, display)

  def create(
    writer: OutputStreamWriter,
    display: RefreshDisplay,
    logChanging: Boolean
  ): RefreshLogger =
    new RefreshLogger(writer, display, fallbackMode = false, logChanging = logChanging)

  def create(
    writer: OutputStreamWriter,
    display: RefreshDisplay,
    logChanging: Boolean,
    logPickedVersions: Boolean
  ): RefreshLogger =
    new RefreshLogger(
      writer,
      display,
      fallbackMode = false,
      logChanging = logChanging,
      logPickedVersions = logPickedVersions
    )

  lazy val defaultFallbackMode: Boolean =
    !coursier.paths.Util.useAnsiOutput()

  private class UpdateDisplayRunnable(out: Writer, val display: RefreshDisplay) extends Runnable {

    private val messages = new ConcurrentLinkedQueue[String]

    def log(message: String): Unit =
      messages.add(message)
    private def flushMessages(): Unit = {
      var printedAnything = false
      var msg: String     = null
      while ({
        msg = messages.poll()
        msg != null
      }) {
        out.write(msg)
        out.write(System.lineSeparator())
        printedAnything = true
      }
      if (printedAnything)
        out.flush()
    }

    private var printedAnything0 = false

    private var stopped = false

    def printedAnything() = printedAnything0

    private val needsUpdate = new AtomicBoolean(false)

    def update(): Unit =
      needsUpdate.set(true)

    private val downloads = new ArrayBuffer[String]
    private val doneQueue = new ArrayBuffer[(String, RefreshInfo)]
    val infos             = new ConcurrentHashMap[String, RefreshInfo]

    def newEntry(
      url: String,
      info: RefreshInfo,
      fallbackMessage: => String
    ): Unit = {
      assert(!infos.containsKey(url), s"Attempts to download $url twice in parallel")
      val prev = infos.putIfAbsent(url, info)
      assert(prev == null, s"Attempts to download $url twice in parallel (second check)")

      display.newEntry(out, url, info)

      downloads.synchronized {
        downloads.append(url)
      }

      update()
    }

    def removeEntry(
      url: String,
      success: Boolean,
      fallbackMessage: => String
    )(
      update0: RefreshInfo => RefreshInfo
    ): Unit = {
      val inf = downloads.synchronized {
        downloads -= url

        val info = infos.remove(url)
        assert(info != null, s"$url was not being downloaded")

        if (success)
          doneQueue += (url -> update0(info))

        info.withSuccess(success)
      }

      display.removeEntry(out, url, inf)

      update()
    }

    def stop(): Unit = {
      flushMessages()
      display.stop(out)
      printedAnything0 = false
      stopped = true
    }

    def run(): Unit =
      if (!stopped) {

        val needsUpdate0 = needsUpdate.getAndSet(false)

        val (done0, downloads0) =
          if (needsUpdate0)
            downloads.synchronized {
              val q = doneQueue
                .toVector
                .sortBy { case (url, _) => url }

              doneQueue.clear()

              val dw = downloads
                .toVector
                .map(url => url -> infos.get(url))
                .sortBy { case (_, info) => -info.fraction.sum }

              (q, dw)
            }
          else
            (Seq.empty, Seq.empty)

        flushMessages()
        display.update(out, done0, downloads0, needsUpdate0)
      }
  }

  private final class State(
    var refCount: Int,
    val runnable: UpdateDisplayRunnable,
    val managedSchedulerOpt: Option[ScheduledExecutorService]
  ) extends AutoCloseable {
    def close(): Unit = {
      for (scheduler <- managedSchedulerOpt) {
        scheduler.shutdown()
        val refreshInterval = runnable.display.refreshInterval
        scheduler.awaitTermination(2 * refreshInterval.length, refreshInterval.unit)
      }
      runnable.stop()
    }
  }
}

// FIXME Default values should be removed in later versions
// (extra constructors are fine, and make it easier to maintain binary compatibility)
class RefreshLogger(
  out: Writer,
  display: RefreshDisplay,
  val fallbackMode: Boolean = RefreshLogger.defaultFallbackMode,
  logChanging: Boolean = false,
  logPickedVersions: Boolean = false,
  schedulerOpt: Option[ScheduledExecutorService] = None
) extends CacheLogger {

  def this(
    out: Writer,
    display: RefreshDisplay
  ) = this(out, display, RefreshLogger.defaultFallbackMode, false, false, None)

  def this(
    out: Writer,
    display: RefreshDisplay,
    fallbackMode: Boolean
  ) = this(out, display, fallbackMode, false, false, None)

  def this(
    out: Writer,
    display: RefreshDisplay,
    fallbackMode: Boolean,
    logChanging: Boolean,
    logPickedVersions: Boolean
  ) = this(out, display, fallbackMode, false, false, None)

  import RefreshLogger._

  private val lock               = new Object
  @volatile private var stateOpt = Option.empty[State]

  private def updateRunnable = stateOpt.map(_.runnable).getOrElse {
    throw new Exception(s"Uninitialized TermDisplay $this")
  }

  override def init(sizeHint: Option[Int]): Unit =
    lock.synchronized {
      stateOpt match {
        case Some(state) =>
          state.refCount += 1
        case None =>
          val (scheduler, managedSchedulerOpt) = schedulerOpt match {
            case Some(scheduler0) => (scheduler0, None)
            case None =>
              val scheduler0 =
                ThreadUtil.fixedScheduledThreadPool(1, name = "coursier-progress-bars")
              (scheduler0, Some(scheduler0))
          }
          val updateRunnable = new UpdateDisplayRunnable(out, display)

          for (n <- sizeHint)
            display.sizeHint(n)

          val refreshInterval = display.refreshInterval

          scheduler.scheduleAtFixedRate(
            updateRunnable,
            refreshInterval.length,
            refreshInterval.length,
            refreshInterval.unit
          )

          stateOpt = Some(
            new State(
              refCount = 1,
              runnable = updateRunnable,
              managedSchedulerOpt = managedSchedulerOpt
            )
          )
      }
    }

  override def stop(): Unit =
    lock.synchronized {
      stateOpt match {
        case Some(state) =>
          state.refCount -= 1
          if (state.refCount <= 0)
            state.close()
        case None =>
        // throw?
      }
    }

  override def checkInitialized(): Unit = {
    updateRunnable
  }

  override def checkingArtifact(url: String, artifact: Artifact): Unit =
    if (logChanging && artifact.changing)
      updateRunnable.log(s"Checking changing artifact $url")

  override def pickedModuleVersion(module: String, version: String): Unit =
    if (logPickedVersions)
      updateRunnable.log(s"Using $module:$version")

  override def downloadingArtifact(url: String, artifact: Artifact): Unit =
    updateRunnable.newEntry(
      url,
      DownloadInfo(0L, 0L, None, System.currentTimeMillis(), updateCheck = false, watching = false),
      s"Downloading $url" + System.lineSeparator()
    )

  override def downloadLength(
    url: String,
    totalLength: Long,
    alreadyDownloaded: Long,
    watching: Boolean
  ): Unit = {
    val info = updateRunnable.infos.get(url)
    assert(info != null, s"Incoherent state ($url)")
    val newInfo = info match {
      case info0: DownloadInfo =>
        info0
          .withLength(Some(totalLength))
          .withPreviouslyDownloaded(alreadyDownloaded)
          .withWatching(watching)
      case _ =>
        throw new Exception(s"Incoherent display state for $url")
    }
    updateRunnable.infos.put(url, newInfo)

    updateRunnable.update()
  }
  override def downloadProgress(url: String, downloaded: Long): Unit = {
    val info = updateRunnable.infos.get(url)
    assert(info != null, s"Incoherent state ($url)")
    val newInfo = info match {
      case info0: DownloadInfo =>
        info0.withDownloaded(downloaded)
      case _ =>
        throw new Exception(s"Incoherent display state for $url")
    }
    updateRunnable.infos.put(url, newInfo)

    updateRunnable.update()
  }

  override def downloadedArtifact(url: String, success: Boolean): Unit = {
    val msg =
      if (success)
        s"Downloaded $url\n"
      else
        s"Failed to download $url\n"
    updateRunnable.removeEntry(url, success, msg)(x => x)
  }

  override def checkingUpdates(url: String, currentTimeOpt: Option[Long]): Unit =
    updateRunnable.newEntry(
      url,
      CheckUpdateInfo(currentTimeOpt, None, isDone = false),
      s"Checking $url\n"
    )

  override def checkingUpdatesResult(
    url: String,
    currentTimeOpt: Option[Long],
    remoteTimeOpt: Option[Long]
  ): Unit = {
    // Not keeping a message on-screen if a download should happen next
    // so that the corresponding URL doesn't appear twice
    val newUpdate = remoteTimeOpt.exists { remoteTime =>
      currentTimeOpt.forall { currentTime =>
        currentTime < remoteTime
      }
    }

    updateRunnable.removeEntry(url, !newUpdate, s"Checked $url\n") {
      case info: CheckUpdateInfo =>
        info
          .withRemoteTimeOpt(remoteTimeOpt)
          .withIsDone(true)
      case _ =>
        throw new Exception(s"Incoherent display state for $url")
    }
  }

}
