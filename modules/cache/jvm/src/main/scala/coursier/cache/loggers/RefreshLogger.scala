package coursier.cache.loggers

import java.io.{OutputStream, OutputStreamWriter, Writer}
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicBoolean

import coursier.cache.CacheLogger
import coursier.cache.internal.Terminal
import coursier.cache.loggers.RefreshInfo.{CheckUpdateInfo, DownloadInfo}

import scala.collection.mutable.ArrayBuffer

object RefreshLogger {

  def defaultDisplay(fallbackMode: Boolean = defaultFallbackMode): RefreshDisplay =
    if (fallbackMode)
      new FallbackRefreshDisplay()
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

  def create(writer: OutputStreamWriter, display: RefreshDisplay): RefreshLogger =
    new RefreshLogger(writer, display)


  def defaultFallbackMode: Boolean = {
    val env0 = sys.env.get("COURSIER_PROGRESS").map(_.toLowerCase).collect {
      case "true"  | "enable"  | "1" => true
      case "false" | "disable" | "0" => false
    }
    def compatibilityEnv = sys.env.get("COURSIER_NO_TERM").nonEmpty

    def nonInteractive = System.console() == null

    def insideEmacs = sys.env.contains("INSIDE_EMACS")
    def ci = sys.env.contains("CI")

    val env = env0.fold(compatibilityEnv)(!_)

    env || nonInteractive || insideEmacs || ci
  }


  private class UpdateDisplayRunnable(out: Writer, val display: RefreshDisplay) extends Runnable {

    private var printedAnything0 = false

    private var stopped = false

    def printedAnything() = printedAnything0

    private val needsUpdate = new AtomicBoolean(false)

    def update(): Unit =
      needsUpdate.set(true)

    private val downloads = new ArrayBuffer[String]
    private val doneQueue = new ArrayBuffer[(String, RefreshInfo)]
    val infos = new ConcurrentHashMap[String, RefreshInfo]

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

        info
      }

      display.removeEntry(out, url, inf)

      update()
    }

    def stop(): Unit = {
      display.clear(out)
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
                .map { url => url -> infos.get(url) }
                .sortBy { case (_, info) => -info.fraction.sum }

              (q, dw)
            }
          else
            (Seq.empty, Seq.empty)

        display.update(out, done0, downloads0, needsUpdate0)
      }
  }

}

class RefreshLogger(
  out: Writer,
  display: RefreshDisplay,
  val fallbackMode: Boolean = RefreshLogger.defaultFallbackMode && Terminal.ttyAvailable
) extends CacheLogger {

  import RefreshLogger._

  private var updateRunnableOpt = Option.empty[UpdateDisplayRunnable]
  @volatile private var scheduler: ScheduledExecutorService = _
  private val lock = new Object

  private def updateRunnable = updateRunnableOpt.getOrElse {
    throw new Exception("Uninitialized TermDisplay")
  }

  override def init(): Unit =
    if (scheduler == null || updateRunnableOpt.isEmpty)
      lock.synchronized {
        if (scheduler == null)
          scheduler = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactory {
              val defaultThreadFactory = Executors.defaultThreadFactory()
              def newThread(r: Runnable) = {
                val t = defaultThreadFactory.newThread(r)
                t.setDaemon(true)
                t.setName("progress-bar")
                t
              }
            }
          )

        if (updateRunnableOpt.isEmpty) {

          updateRunnableOpt = Some(new UpdateDisplayRunnable(out, display))

          val refreshInterval = display.refreshInterval

          scheduler.scheduleAtFixedRate(
            updateRunnable,
            refreshInterval.length,
            refreshInterval.length,
            refreshInterval.unit
          )
        }
      }

  override def stop(): Unit =
    if (scheduler != null || updateRunnableOpt.nonEmpty)
      lock.synchronized {
        if (scheduler != null) {
          scheduler.shutdown()
          for (r <- updateRunnableOpt) {
            val refreshInterval = r.display.refreshInterval
            scheduler.awaitTermination(2 * refreshInterval.length, refreshInterval.unit)
          }
          scheduler = null
        }

        if (updateRunnableOpt.nonEmpty) {
          updateRunnable.stop()
          updateRunnableOpt = None
        }
      }

  override def downloadingArtifact(url: String): Unit =
    updateRunnable.newEntry(
      url,
      DownloadInfo(0L, 0L, None, System.currentTimeMillis(), updateCheck = false, watching = false),
      s"Downloading $url\n"
    )

  override def downloadLength(url: String, totalLength: Long, alreadyDownloaded: Long, watching: Boolean): Unit = {
    val info = updateRunnable.infos.get(url)
    assert(info != null, s"Incoherent state ($url)")
    val newInfo = info match {
      case info0: DownloadInfo =>
        info0.copy(
          length = Some(totalLength),
          previouslyDownloaded = alreadyDownloaded,
          watching = watching
        )
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
        info0.copy(downloaded = downloaded)
      case _ =>
        throw new Exception(s"Incoherent display state for $url")
    }
    updateRunnable.infos.put(url, newInfo)

    updateRunnable.update()
  }

  override def downloadedArtifact(url: String, success: Boolean): Unit =
    updateRunnable.removeEntry(url, success, s"Downloaded $url\n")(x => x)

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
        info.copy(remoteTimeOpt = remoteTimeOpt, isDone = true)
      case _ =>
        throw new Exception(s"Incoherent display state for $url")
    }
  }

}
