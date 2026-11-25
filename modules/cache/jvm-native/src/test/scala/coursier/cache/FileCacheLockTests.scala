package coursier.cache

import coursier.cache.TestUtil._
import coursier.util.{Artifact, Sync, Task}
import utest._

/** Behavioural baseline for the locking done around downloads.
  *
  * These assert on the requests the server receives, not only on the resulting content: a lock
  * released too early, or not taken at all, yields the very same (correct) content, just downloaded
  * several times.
  *
  * The cases that simulate a *competing process* by taking a lock from another thread live in
  * `FileCacheLockJvmTests` - that simulation only works on the JVM.
  */
object FileCacheLockTests extends TestSuite {

  private val content = "the artifact content, long enough to be downloaded in several chunks " * 32

  private def handler(
    slowDownload: Boolean = false,
    found: Boolean = true
  )(
    entry: RequestLog.Entry
  ): RawHttpServer.Response =
    // HEAD is served too: the watcher path does HEAD requests to get the length of the download it
    // is watching, and a 404 there would hand it a bogus length
    if (found && entry.path == "/dir/foo.jar") {
      if (slowDownload && entry.method == "GET")
        Thread.sleep(500L)
      RawHttpServer.ok(content)
    }
    else
      RawHttpServer.notFound

  private def testCache(cacheDir: os.Path): FileCache[Task] =
    FileCache[Task](cacheDir.toIO).copy(
      checksums = Nil,
      cachePolicies = Seq(CachePolicy.FetchMissing),
      // a pool of our own, so that the number of threads available doesn't depend on what else
      // runs in this JVM
      pool = Sync.fixedThreadPool(8)
    )

  private def lockOrPartFiles(cacheDir: os.Path): Seq[os.SubPath] =
    if (os.exists(cacheDir))
      os.walk(cacheDir)
        .filter(os.isFile)
        .filter(p => p.last.endsWith(".lock") || p.last.endsWith(".part"))
        .map(_.relativeTo(cacheDir).asSubPath)
    else
      Nil

  val tests = Tests {

    test("only one download for concurrent requests on the same artifact") {

      val log = new RequestLog

      RawHttpServer.withServer(log)(handler(slowDownload = true)) { baseUrl =>
        withTmpDir { dir =>

          val cache    = testCache(dir / "cache")
          val artifact = Artifact(s"$baseUrl/dir/foo.jar")

          val results = runConcurrently(4) { _ =>
            cache.file(artifact).run.unsafeRun(wrapExceptions = true)(cache.ec)
          }

          for (res <- results) {
            val f = res.fold(e => throw new Exception(e.describe), identity)
            assert(os.read(os.Path(f)) == content)
          }

          // the point of the test: the other callers waited for the download in flight rather
          // than starting one of their own
          val getCount = log.count("GET")
          if (getCount != 1)
            sys.error(s"Expected exactly one GET, got $getCount (requests: ${log.methodsAndPaths})")

          // … and that they did wait, rather than find the artifact already in cache: the HEAD
          // requests are the ones the watcher path does to report the progress of the download
          // it is watching
          assert(log.count("HEAD") > 0)

          assert(lockOrPartFiles(dir / "cache").isEmpty)
        }
      }
    }

    test("lock file cleanup") {

      def check(found: Boolean, expectSuccess: Boolean): Unit =
        RawHttpServer.withServer(new RequestLog)(handler(found = found)) { baseUrl =>
          withTmpDir { dir =>
            val cache = testCache(dir / "cache")
            val res = cache
              .file(Artifact(s"$baseUrl/dir/foo.jar")).run
              .unsafeRun(wrapExceptions = true)(cache.ec)
            assert(res.isRight == expectSuccess)
            val leftOver = lockOrPartFiles(dir / "cache")
            if (leftOver.nonEmpty)
              sys.error(s"Left over lock / part files: $leftOver")
          }
        }

      test("after a successful download") {
        check(found = true, expectSuccess = true)
      }
      test("after a failed download") {
        check(found = false, expectSuccess = false)
      }
    }

  }
}
