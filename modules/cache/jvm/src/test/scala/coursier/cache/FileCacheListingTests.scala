package coursier.cache

import cats.effect.IO
import coursier.cache.RequestLog.logRequests
import coursier.cache.TestUtil._
import coursier.util.{Artifact, Task}
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import utest._

import java.io.File

/** Behavioural baseline for directory listings - the `.directory` files a URL ending in `/` is
  * cached as, and the `.links` auxiliary file `fetchPerPolicy` reads back.
  *
  * Neither had any coverage. Both are reached through `fetch` rather than `file`, and the `.links`
  * side of it is written by `doTouchCheckFile(updateLinks = true)` at download time, so a listing
  * that is served from cache and one that was just downloaded go through different code.
  */
object FileCacheListingTests extends TestSuite {

  private val page =
    """<!DOCTYPE html>
      |<html>
      |<head></head>
      |<body>
      |<ul>
      |<li><a href="../">../</a></li>
      |<li><a href="bar.pom">bar.pom</a></li>
      |<li><a href="foo.jar">foo.jar</a></li>
      |<li><a href="sub/">sub/</a></li>
      |</ul>
      |</body>
      |</html>
      |""".stripMargin

  /** What `WebPage.listElements` keeps out of `page`: `../` is filtered out. */
  private val expectedElements = "bar.pom\nfoo.jar\nsub/"

  private def routes: HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case GET -> Root / "dir" / ""  => Ok(page)
      case HEAD -> Root / "dir" / "" => Ok(page)
    }

  private def testCache(dir: os.Path): FileCache[Task] =
    FileCache[Task]((dir / "cache").toIO)
      .withChecksums(Nil)
      .withCachePolicies(Seq(CachePolicy.FetchMissing))

  private def fetch(cache: FileCache[Task], url: String): Either[String, String] =
    cache.fetch(Artifact(url)).run.unsafeRun(wrapExceptions = true)(cache.ec)

  /** Everything under the cache directory, as paths relative to it. */
  private def cacheLayout(cache: FileCache[Task], dir: os.Path): Seq[String] = {
    val cacheDir = dir / "cache"
    if (os.exists(cacheDir))
      os.walk(cacheDir).filter(os.isFile).map(_.relativeTo(cacheDir).last).sorted
    else
      Nil
  }

  /** A `listing` directory holding the same entries the HTML page advertises. */
  private def makeLocalListing(dir: os.Path): os.Path = {
    val listing = dir / "listing"
    os.write(listing / "bar.pom", "the pom", createFolders = true)
    os.write(listing / "foo.jar", "the jar")
    os.makeDir.all(listing / "sub")
    listing
  }

  val tests = Tests {

    test("over http") {

      test("a URL ending in a slash is cached as a .directory file") {

        val log = new RequestLog

        withHttpServer(logRequests(log)(routes)) { serverUri =>
          withTmpDir { dir =>

            val cache = testCache(dir)
            val url   = (serverUri / "dir" / "").renderString
            assert(url.endsWith("/"))

            val res = fetch(cache, url).fold(e => sys.error(e), identity)
            assert(res == page)
            assert(log.methodsAndPaths == List(("GET", "/dir/")))

            // the page itself, its .checked stamp, and the listing extracted out of it
            assert(cacheLayout(cache, dir) == Seq(
              "..directory__links",
              "..directory.checked",
              ".directory"
            ).sorted)

            assert(os.Path(cache.localFile(url)).last == ".directory")
          }
        }
      }

      test("the .links suffix reads the extracted listing back") {

        val log = new RequestLog

        withHttpServer(logRequests(log)(routes)) { serverUri =>
          withTmpDir { dir =>

            val cache = testCache(dir)
            val url   = (serverUri / "dir" / "").renderString

            val res = fetch(cache, s"$url.links").fold(e => sys.error(e), identity)
            assert(res == expectedElements)

            // the `.links` suffix is stripped before anything is downloaded: the directory itself
            // is what is fetched
            assert(log.methodsAndPaths == List(("GET", "/dir/")))
          }
        }
      }

      test("the extracted listing is what is served, not a re-parse of the page") {

        // `fetchPerPolicy` prefers the `..directory__links` file when it is there, and only falls
        // back to parsing `.directory` when it isn't. Pinned by making the two disagree.

        val log = new RequestLog

        withHttpServer(logRequests(log)(routes)) { serverUri =>
          withTmpDir { dir =>

            val cache = testCache(dir)
            val url   = (serverUri / "dir" / "").renderString

            assert(fetch(cache, url).isRight)

            val linkFile = new File(cache.localFile(url).getParentFile, "..directory__links")
            assert(linkFile.isFile)
            os.write.over(os.Path(linkFile), "something-else.jar")

            val res = fetch(cache, s"$url.links").fold(e => sys.error(e), identity)
            assert(res == "something-else.jar")

            // and once it is gone, the page is parsed again
            os.remove(os.Path(linkFile))
            val res0 = fetch(cache, s"$url.links").fold(e => sys.error(e), identity)
            assert(res0 == expectedElements)

            // all of that off the cached copy, without going back to the server
            assert(log.methodsAndPaths == List(("GET", "/dir/")))
          }
        }
      }
    }

    test("over file") {

      test("a directory is listed as an HTML page") {
        withTmpDir { dir =>

          val cache   = testCache(dir)
          val listing = makeLocalListing(dir)
          val url     = directoryUrl(listing)

          val res = fetch(cache, url).fold(e => sys.error(e), identity)

          // the same shape as what a repository server would serve, generated on the fly
          for (name <- Seq("bar.pom", "foo.jar", "sub/"))
            assert(res.contains(s"""<li><a href="$name">$name</a></li>"""))

          // read in place: nothing is cached, and no `.directory` file is created
          assert(cacheLayout(cache, dir).isEmpty)
          assert(os.list(listing).map(_.last).sorted == Seq("bar.pom", "foo.jar", "sub"))
        }
      }

      test("the .links suffix lists the entries directly") {
        withTmpDir { dir =>

          val cache   = testCache(dir)
          val listing = makeLocalListing(dir)
          val url     = directoryUrl(listing)

          val res = fetch(cache, s"$url.links").fold(e => sys.error(e), identity)
          assert(res == expectedElements)
          assert(cacheLayout(cache, dir).isEmpty)
        }
      }

      test("a missing directory is an error") {
        withTmpDir { dir =>
          val cache = testCache(dir)
          val res   = fetch(cache, directoryUrl(dir / "nope"))
          assert(res.isLeft)
        }
      }
    }
  }
}
