package coursier.cache

import coursier.cache.TestUtil._
import coursier.util.{Artifact, Sync, Task}
import utest._

import java.io.{BufferedReader, File, InputStreamReader}
import java.net.{InetAddress, ServerSocket, Socket, SocketTimeoutException}
import java.nio.charset.StandardCharsets.US_ASCII
import java.util.concurrent.{Executors, ThreadFactory}
import java.util.concurrent.atomic.{AtomicInteger, AtomicReference}

import scala.collection.mutable
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.{DurationInt, FiniteDuration}

/** What happens when a repository answers slowly, or stops answering altogether.
  *
  * A connection that goes quiet without failing - dropped by a NAT or a load balancer, say - used
  * to block the pool thread reading it for as long as the JVM lived: nothing ever threw, so nothing
  * retried and nothing failed. Every other caller of that URL in the JVM then piled up behind it,
  * backing off exponentially towards hours. Together that wedged whole builds until their CI job
  * timed out, with no error to show for it.
  */
object FileCacheTimeoutTests extends TestSuite {

  private def cacheOf(dir: os.Path): FileCache[Task] =
    FileCache[Task]((dir / "cache").toIO)
      .withChecksums(Nil)
      .withCachePolicies(Seq(CachePolicy.FetchMissing))
      // a pool of our own, so that the number of threads available doesn't depend on what else
      // runs in this JVM
      .withPool(Sync.fixedThreadPool(8))

  private def fetch(cache: FileCache[Task], url: String): Either[ArtifactError, File] =
    cache.file(Artifact(url)).run.unsafeRun(wrapExceptions = true)(cache.ec)

  /** Runs `f` `n` times in parallel, each on its own thread, and awaits all results */
  private def runConcurrently[T](
    n: Int,
    timeout: FiniteDuration = 5.minutes
  )(f: Int => T): Seq[T] = {
    val pool = Executors.newFixedThreadPool(
      n,
      new ThreadFactory {
        val count = new AtomicInteger
        def newThread(r: Runnable): Thread = {
          val t = new Thread(r, s"timeout-test-${count.incrementAndGet()}")
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

  private def causes(t: Throwable): List[Throwable] =
    if (t == null) Nil else t :: causes(t.getCause)

  /** Runs `f` on a thread of its own, and fails if it hasn't returned within `deadline` */
  private def withDeadline[T](deadline: FiniteDuration)(f: => T): T = {
    val result = new AtomicReference[Either[Throwable, T]]
    val t = new Thread("timeout-test-deadline") {
      override def run(): Unit =
        result.set(
          try Right(f)
          catch { case e: Throwable => Left(e) }
        )
    }
    t.setDaemon(true)
    t.start()
    t.join(deadline.toMillis)
    result.get() match {
      case null       => sys.error(s"Still running after $deadline")
      case Right(res) => res
      case Left(e)    => throw e
    }
  }

  val tests = Tests {

    test("a download that stops answering fails rather than hanging") {

      // long enough that a download stuck on it would be obvious, short enough to test
      val readTimeout = 2.seconds
      val attempts    = 2

      RawServer.withServer(RawServer.stallAfterHeaders(bodyLength = 23450)) { server =>
        withTmpDir { dir =>
          val cache = cacheOf(dir)
            .withReadTimeout(Some(readTimeout))
            .withRetry(attempts)

          // the fetch runs on a thread of its own: without a read timeout it never returns at
          // all, and that should be reported as a failed test rather than a stuck test run
          val res = withDeadline(readTimeout * attempts + 30.seconds) {
            fetch(cache, server.url("/dir/foo.jar"))
          }

          val err = res match {
            case Left(e)  => e
            case Right(f) => sys.error(s"Expected a failure, got $f")
          }
          // the point of the test: the read timed out, rather than blocking forever
          assert(causes(err).exists(_.isInstanceOf[SocketTimeoutException]))
        }
      }
    }

    test("waiting on a download in flight doesn't outlast it") {

      // the delay between polls used to double without bound, so a waiter only noticed the
      // download had landed at 100ms, 300ms, 700ms, 1.5s, 3.1s, 6.4s, … after it started. A
      // download that finishes just past one of those left every waiter asleep until the next.
      val initialDelay = 100.millis
      val downloadTime = 3400.millis
      // how much longer than the download a waiter may take. Capped, a waiter is at most one
      // poll behind; uncapped, these ones wouldn't wake up before ~6.4s
      val slack   = 1.second
      val callers = 4

      RawServer.withServer(RawServer.slowBody(bodyLength = 23450, over = downloadTime)) { server =>
        withTmpDir { dir =>
          val cache = cacheOf(dir).withRetryBackoffInitialDelay(initialDelay)
          val url   = server.url("/dir/foo.jar")

          val start = System.currentTimeMillis()
          val elapsed = runConcurrently(callers) { _ =>
            val res = fetch(cache, url)
            res.fold(e => sys.error(e.describe), _ => ())
            System.currentTimeMillis() - start
          }

          val slowest = elapsed.max
          assert(slowest < (downloadTime + slack).toMillis)

          // only one caller downloads; the others watch it, and watching should cost one length
          // lookup per waiter, not one per poll of the download in flight
          val getCount  = server.count("GET")
          val headCount = server.count("HEAD")
          assert(getCount == 1)
          assert(headCount <= callers)
        }
      }
    }
  }

  /** A server that can answer in ways a well-behaved one won't: slowly, or not at all.
    *
    * Requests are handled on a thread each, since these tests are about what concurrent callers of
    * one URL do to each other.
    */
  private final class RawServer(handle: (String, Socket) => Unit) extends AutoCloseable {

    private val server           = new ServerSocket(0, 50, InetAddress.getByName("localhost"))
    private val requests         = new mutable.ArrayBuffer[String]
    private val open             = new mutable.ArrayBuffer[Socket]
    @volatile private var closed = false

    def url(path: String): String = s"http://localhost:${server.getLocalPort}$path"

    def count(method: String): Int = requests.synchronized {
      requests.count(_ == method)
    }

    private val acceptThread = {
      val t = new Thread("raw-timeout-server") {
        override def run(): Unit =
          while (!closed)
            try {
              val socket = server.accept()
              open.synchronized(open += socket)
              val handler = new Thread("raw-timeout-server-handler") {
                override def run(): Unit =
                  try {
                    val reader =
                      new BufferedReader(new InputStreamReader(socket.getInputStream, US_ASCII))
                    val requestLine = reader.readLine()
                    var line        = reader.readLine()
                    while (line != null && line.nonEmpty) line = reader.readLine()
                    if (requestLine != null) {
                      val method = requestLine.takeWhile(_ != ' ')
                      requests.synchronized(requests += method)
                      handle(method, socket)
                    }
                  }
                  catch {
                    case _: Exception if closed => // shutting down
                  }
              }
              handler.setDaemon(true)
              handler.start()
            }
            catch {
              case _: Exception if closed => // shutting down
            }
      }
      t.setDaemon(true)
      t.start()
      t
    }

    def close(): Unit = {
      closed = true
      // the stalling handlers never close their socket, so close it for them
      open.synchronized {
        open.foreach(s =>
          try s.close()
          catch { case _: Exception => }
        )
        open.clear()
      }
      server.close()
      acceptThread.join(5000L)
    }
  }

  private object RawServer {

    private def headers(length: Int): Array[Byte] =
      s"HTTP/1.1 200 OK\r\nContent-Length: $length\r\n\r\n".getBytes(US_ASCII)

    private def body(length: Int): Array[Byte] =
      Array.fill[Byte](length)('a')

    /** Announces a body, sends a little of it, then goes quiet without closing the connection */
    def stallAfterHeaders(bodyLength: Int): (String, Socket) => Unit =
      (method, socket) => {
        val out = socket.getOutputStream
        out.write(headers(bodyLength))
        if (method == "GET")
          out.write(body(bodyLength).take(128))
        out.flush()
        if (method != "GET")
          socket.close()
        // and never anything else: the connection is left open and silent
      }

    /** Serves the body in chunks spread over `over` */
    def slowBody(bodyLength: Int, over: FiniteDuration): (String, Socket) => Unit =
      (method, socket) => {
        val out     = socket.getOutputStream
        val content = body(bodyLength)
        out.write(headers(bodyLength))
        out.flush()
        if (method == "GET") {
          val chunks = 10
          for (i <- 0 until chunks) {
            Thread.sleep(over.toMillis / chunks)
            val from = i * bodyLength / chunks
            val to   = (i + 1) * bodyLength / chunks
            out.write(content, from, to - from)
            out.flush()
          }
        }
        out.flush()
        socket.close()
      }

    def withServer[T](handle: (String, Socket) => Unit)(f: RawServer => T): T = {
      val server = new RawServer(handle)
      try f(server)
      finally server.close()
    }
  }
}
