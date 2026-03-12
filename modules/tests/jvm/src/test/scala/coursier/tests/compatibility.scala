package coursier.tests

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}

import coursier.core.Repository
import coursier.paths.Util
import coursier.testcache.TestCache
import coursier.util.{Sync, Task}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future}

object compatibility {

  implicit val executionContext: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(TestCache.pool)

  private lazy val testDataDir =
    Option(System.getenv("COURSIER_TEST_DATA_DIR")).getOrElse {
      sys.error("COURSIER_TEST_DATA_DIR env var not set")
    }

  def textResource(path: String): Future[String] = Future {
    val f = new File(s"$testDataDir/$path")
    new String(Files.readAllBytes(f.toPath), UTF_8)
  }

  def artifact[F[_]: Sync]: Repository.Fetch[F] =
    TestCache.cache[F].fetch
  def artifactWithProxy[F[_]: Sync](proxy: java.net.Proxy): Repository.Fetch[F] =
    TestCache.cache[F].withProxy(Some(proxy)).fetch

  val taskArtifact = artifact[Task]

  private lazy val baseResources = {
    val dir = Paths.get(testDataDir)
    assert(Files.isDirectory(dir))
    dir
  }

  def tryCreate(path: String, content: String): Unit =
    if (TestCache.updateSnapshots) {
      val path0 = baseResources.resolve(path)
      Util.createDirectories(path0.getParent)
      Files.write(path0, content.getBytes(UTF_8))
    }

}
