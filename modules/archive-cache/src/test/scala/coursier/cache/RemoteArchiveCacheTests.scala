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
        val port = freePort()
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

    test("server checks checksums") {
      withRemoteCache { (_, remoteCache, _) =>
        implicit val ec: ExecutionContext = remoteCache.ec
        val archive = remoteCache.file(artifact).run.unsafeRun()
          .fold(err => throw new Exception(err), os.Path(_))
        val content = os.read.bytes(archive)
        val sha256 = java.security.MessageDigest.getInstance("SHA-256")
          .digest(content).map(b => f"$b%02x").mkString
        val digestFile = archive / os.up / s".${archive.last}__sha256.computed"

        def fileWithChecksum(sha256: String) =
          remoteCache.fileWithChecks(
            artifact,
            integrityCheck = false,
            expectedChecksum = Some(ExpectedChecksum("SHA-256", sha256))
          )(Task.sync).run.unsafeRun()

        assert(fileWithChecksum(sha256) == Right(archive.toIO))
        // the server persisted the digest
        assert(os.read.bytes(digestFile).map(b => f"$b%02x").mkString == sha256)

        // wrong checksum: the server downloads the archive again, then fails
        val wrongSha256 = "ab" * 32
        val res         = fileWithChecksum(wrongSha256)
        assert(res.left.exists(_.getMessage.contains("wrong checksum")))
        assert(res.left.exists(_.getMessage.contains(wrongSha256)))
        assert(os.read.bytes(archive).toSeq == content.toSeq)

        // archive with a wrong checksum in the server cache (as per the persisted digest): the
        // server downloads it again
        os.write.over(
          digestFile,
          wrongSha256.grouped(2).map(Integer.parseInt(_, 16).toByte).toArray[Byte]
        )
        assert(fileWithChecksum(sha256) == Right(archive.toIO))
        assert(os.read.bytes(digestFile).map(b => f"$b%02x").mkString == sha256)
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
