package coursier.cache

import java.io.File
import java.math.BigInteger
import java.nio.file.{Files, Path}
import java.security.MessageDigest

import java.util.concurrent.{Executors, ThreadFactory}
import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.language.implicitConversions

object TestUtil extends PlatformTestUtil {

  private def deleteRecursive(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles().foreach(deleteRecursive)
    f.delete()
  }

  def withTmpDir[T](f: os.Path => T): T = {
    val dir = os.temp.dir(prefix = "coursier-test")
    try f(dir)
    finally os.remove.all(dir)
  }

  /** The `file:` URL of `path`.
    *
    * Interpolating a path into `file://…` gives `file://C:\dir\foo.jar` on Windows, whose drive
    * letter is then parsed as the host. `Path#toUri` gives the `file:///C:/dir/foo.jar` form that
    * both `CachePath.localFile` and `URL#openConnection` handle.
    */
  def fileUrl(path: os.Path): String =
    path.toNIO.toUri.toString

  /** The `file:` URL of a directory, with the trailing slash coursier keys directory listings on.
    *
    * `Path#toUri` only adds that slash for directories that exist.
    */
  def directoryUrl(path: os.Path): String =
    fileUrl(path).stripSuffix("/") + "/"

  def withTmpDir0[T](f: Path => T): T = {
    val dir = Files.createTempDirectory("coursier-test")
    val shutdownHook: Thread =
      new Thread {
        override def run() =
          deleteRecursive(dir.toFile)
      }
    Runtime.getRuntime.addShutdownHook(shutdownHook)
    try f(dir)
    finally {
      deleteRecursive(dir.toFile)
      Runtime.getRuntime.removeShutdownHook(shutdownHook)
    }
  }

  /** Runs `f` `n` times in parallel, on a dedicated pool, and awaits all results.
    *
    * Each call gets its own thread, so that `f` is free to block (which `Task#unsafeRun` does).
    */
  def runConcurrently[T](
    n: Int,
    timeout: FiniteDuration = 2.minutes
  )(
    f: Int => T
  ): Seq[T] = {
    val pool = Executors.newFixedThreadPool(
      n,
      new ThreadFactory {
        val count = new AtomicInteger
        def newThread(r: Runnable): Thread = {
          val t = new Thread(r, s"test-concurrent-${count.incrementAndGet()}")
          t.setDaemon(true)
          t
        }
      }
    )
    try {
      implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(pool)
      // all futures are started before any of them is awaited
      val futures = (0 until n).map(idx => Future(f(idx)))
      Await.result(Future.sequence(futures), timeout)
    }
    finally pool.shutdown()
  }

  /** Copies a file and all files in the same folder with the same name plus a suffix
    * @return
    *   `file` in the new location
    */
  def copiedWithMetaTo(file: File, toDir: Path): Path = {
    val dir = file.getParentFile

    val fromTo: Map[File, Path] =
      dir
        .list((_: File, name: String) => name.startsWith(file.getName))
        .map(name => new File(dir, name) -> toDir.resolve(name))
        .toMap

    fromTo.foreach { case (from, to) => Files.copy(from.toPath, to) }

    fromTo(file)
  }

  private def checksum(b: Array[Byte], alg: String, len: Int): String = {
    val md     = MessageDigest.getInstance(alg)
    val digest = md.digest(b)
    val res    = new BigInteger(1, digest).toString(16)
    if (res.length < len)
      ("0" * (len - res.length)) + res
    else
      res
  }

  private val httpDays =
    Array("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
  private val httpMonths =
    Array("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

  /** An instant in the canonical HTTP-date form, for `Last-Modified` and friends.
    *
    * Spelled out rather than left to
    * `DateTimeFormatter.ofPattern("EEE, dd MMM yyyy …", Locale.US)`, whose `EEE` and `MMM` come out
    * empty on Scala Native: scala-java-locales ships only the ROOT locale, and pulling in one of
    * its locale databases just for this is not worth it. See `scala-native-issues.md`.
    */
  def httpDate(instant: java.time.Instant): String = {
    val dt = java.time.ZonedDateTime.ofInstant(instant, java.time.ZoneOffset.UTC)
    f"${httpDays(dt.getDayOfWeek.getValue - 1)}, ${dt.getDayOfMonth}%02d " +
      f"${httpMonths(dt.getMonthValue - 1)} ${dt.getYear}%04d " +
      f"${dt.getHour}%02d:${dt.getMinute}%02d:${dt.getSecond}%02d GMT"
  }

  def sha256(b: Array[Byte]): String =
    checksum(b, "SHA-256", 64)
  def sha1(b: Array[Byte]): String =
    checksum(b, "SHA-1", 40)
  def md5(b: Array[Byte]): String =
    checksum(b, "MD5", 32)

}
