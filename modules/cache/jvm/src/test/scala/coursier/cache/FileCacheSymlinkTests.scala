package coursier.cache

import coursier.cache.TestUtil._
import coursier.paths.CachePath
import coursier.util.{Artifact, Task}
import utest._

import java.nio.file.Files

import scala.util.Properties

/** Symbolic links planted in the cache, by someone else having write access to it, must not make
  * coursier create, truncate, or write to the files they point to.
  */
object FileCacheSymlinkTests extends TestSuite {

  private val body: Array[Byte] =
    (0 until 512).map(i => ('a' + (i % 26)).toChar).mkString.getBytes("UTF-8")

  private val sentinelContent = "precious content, that shouldn't be touched"

  private def testCache(dir: os.Path): FileCache[Task] =
    FileCache[Task]((dir / "cache").toIO).copy(
      checksums = Nil,
      cachePolicies = Seq(CachePolicy.FetchMissing)
    )

  private def run(cache: FileCache[Task], url: String) =
    cache.file(Artifact(url)).run.unsafeRun(wrapExceptions = true)(cache.ec)

  private def symlink(link: os.Path, target: os.Path): Unit = {
    os.makeDir.all(link / os.up)
    Files.createSymbolicLink(link.toNIO, target.toNIO)
  }

  private def checkSentinel(sentinel: os.Path): Unit = {
    assert(os.isFile(sentinel, followLinks = false))
    val content = os.read(sentinel)
    assert(content == sentinelContent)
  }

  private def posixOnly(f: => Unit): Any =
    if (Properties.isWin) "disabled"
    else f

  val tests = Tests {

    test("symlinked .part file") {
      posixOnly {
        RawHttpServer.withServer(new RequestLog) { req =>
          RawHttpServer.rangeStart(req) match {
            case Some(from) => RawHttpServer.partialContent(body, from)
            case None       => RawHttpServer.ok(body)
          }
        } { baseUrl =>
          withTmpDir { dir =>
            val cache    = testCache(dir)
            val url      = s"$baseUrl/dir/foo.jar"
            val sentinel = dir / "sentinel"
            os.write(sentinel, sentinelContent)
            symlink(os.Path(CachePath.temporaryFile(cache.localFile(url))), sentinel)

            val f = run(cache, url).fold(e => throw new Exception(e.describe), identity)

            checkSentinel(sentinel)
            // the whole artifact, not a download "resumed" from the sentinel content
            assert(os.isFile(os.Path(f), followLinks = false))
            assert(java.util.Arrays.equals(os.read.bytes(os.Path(f)), body))
          }
        }
      }
    }

    test("symlinked lock file") {
      posixOnly {
        RawHttpServer.withServer(new RequestLog) { _ =>
          RawHttpServer.ok(body)
        } { baseUrl =>
          withTmpDir { dir =>
            val cache = testCache(dir)
            val url   = s"$baseUrl/dir/foo.jar"
            // dangling: following it would create that file
            val target = dir / "not-created"
            symlink(os.Path(CachePath.lockFile(cache.localFile(url))), target)

            val f = run(cache, url).fold(e => throw new Exception(e.describe), identity)

            assert(!os.exists(target, followLinks = false))
            assert(java.util.Arrays.equals(os.read.bytes(os.Path(f)), body))
          }
        }
      }
    }

    test("symlinked structure lock") {
      posixOnly {
        withTmpDir { dir =>
          val cache    = dir / "cache"
          val sentinel = dir / "sentinel"
          os.write(sentinel, sentinelContent)
          symlink(cache / ".structure.lock", sentinel)

          val res = CacheLocks.withStructureLock(cache.toIO)(2)
          assert(res == 2)
          checkSentinel(sentinel)

          symlink(cache / ".structure.lock", sentinel)

          val res0 = CacheLocks.withStructureLock(cache.toNIO)(3)
          assert(res0 == 3)
          checkSentinel(sentinel)
        }
      }
    }

    test("symlinked error file") {
      posixOnly {
        RawHttpServer.withServer(new RequestLog) { _ =>
          RawHttpServer.notFound
        } { baseUrl =>
          withTmpDir { dir =>
            val cache = testCache(dir)
            val url   = s"$baseUrl/dir/foo.jar"
            // dangling: following it would create that file
            val target    = dir / "not-created"
            val localFile = cache.localFile(url)
            symlink(os.Path(localFile.getParentFile) / s".${localFile.getName}.error", target)

            // errors are only cached for changing artifacts (or with "cache-errors")
            val res = cache.file(Artifact(url).copy(changing = true)).run
              .unsafeRun(wrapExceptions = true)(cache.ec)
            assert(res.isLeft)
            assert(!os.exists(target, followLinks = false))
          }
        }
      }
    }
  }
}
