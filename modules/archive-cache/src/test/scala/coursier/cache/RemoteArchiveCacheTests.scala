package coursier.cache

import java.net.ServerSocket
import java.util.concurrent.{ExecutorService, Executors}

import scala.concurrent.ExecutionContext

import io.undertow.Undertow
import utest._

import coursier.cache.TestUtil._
import coursier.cache.server.CacheServer
import coursier.util.{Artifact, Task}

object RemoteArchiveCacheTests extends TestSuite {

  // random gzip file found on Maven Central
  private val artifact = Artifact(
    "https://repo1.maven.org/maven2/org/danbrough/kotlinxtras/curl/binaries/curlLinuxX64/7_86_0/curlLinuxX64-7_86_0.gz"
  )

  private def freePort(): Int = {
    val socket = new ServerSocket(0)
    try socket.getLocalPort
    finally socket.close()
  }

  private def withExecutorService[T](pool: ExecutorService)(f: ExecutorService => T): T =
    try f(pool)
    finally pool.shutdownNow()

  private def withRemoteCache[T](f: (os.Path, RemoteCache[Task], ArchiveCache[Task]) => T): T =
    withTmpDir { dir =>
      val cacheDir = dir / "cache"
      withExecutorService(Executors.newCachedThreadPool()) { pool =>
        val port   = freePort()
        val server = Undertow.builder()
          .addHttpListener(port, "localhost")
          .setHandler(
            CacheServer.handler(
              FileCache[Task](cacheDir.toIO).copy(pool = pool),
              ExecutionContext.fromExecutorService(pool)
            )
          )
          .build()
        server.start()
        try {
          val remoteCache = RemoteCache[Task](s"http://localhost:$port", cacheDir.toIO)
            .copy(pool = pool, watchLenPool = pool)
          val archiveCache = ArchiveCache[Task]((dir / "arc").toIO, remoteCache)
          f(cacheDir, remoteCache, archiveCache)
        }
        finally server.stop()
      }
    }

  private def integrityFiles(dir: os.Path): Seq[os.Path] =
    os.walk(dir).filter(_.last.endsWith("__integrity"))

  val tests = Tests {

    test("server checks integrity") {
      withRemoteCache { (cacheDir, remoteCache, archiveCache) =>
        val extracted = archiveCache.get(artifact).unsafeRun()(remoteCache.ec)
          .fold(err => throw new Exception(err), os.Path(_))
        assert(os.isFile(extracted))

        // the server wrote an integrity file next to the archive
        val archive = remoteCache.file(artifact).run.unsafeRun()(remoteCache.ec)
          .fold(err => throw new Exception(err), os.Path(_))
        val expectedIntegrityFile = archive / os.up / s".${archive.last}__integrity"
        assert(integrityFiles(cacheDir) == Seq(expectedIntegrityFile))
        assert(integrityFiles(os.Path(archiveCache.location)).isEmpty)
      }
    }

    test("corrupted archive") {
      withRemoteCache { (_, remoteCache, archiveCache) =>
        val archive = remoteCache.file(artifact).run.unsafeRun()(remoteCache.ec)
          .fold(err => throw new Exception(err), os.Path(_))
        val content = os.read.bytes(archive)

        val modifiedTime = os.mtime(archive)
        os.write.over(archive, content.take(content.length - 200))
        os.mtime.set(archive, modifiedTime)

        archiveCache.get(artifact).unsafeRun()(remoteCache.ec)
          .fold(err => throw new Exception(err), os.Path(_))

        // the server should have downloaded the archive again
        assert(os.read.bytes(archive).toSeq == content.toSeq)
      }
    }

    test("no integrity check if disabled") {
      withRemoteCache { (cacheDir, remoteCache, archiveCache) =>
        archiveCache.copy(integrityCheck = Some(false)).get(artifact).unsafeRun()(remoteCache.ec)
          .fold(err => throw new Exception(err), os.Path(_))
        assert(integrityFiles(cacheDir).isEmpty)
      }
    }
  }
}
