package coursier.tests

import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}

import coursier.cache.MockCache
import coursier.core.Repository
import coursier.paths.Util
import coursier.util.{Sync, Task}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService, Future}

object compatibility {

  private val pool = Sync.fixedThreadPool(6)
  implicit val executionContext: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(pool)

  private lazy val testDataDir =
    Option(System.getenv("COURSIER_TEST_DATA_DIR")).getOrElse {
      sys.error("COURSIER_TEST_DATA_DIR env var not set")
    }

  def textResource(path: String): Future[String] = Future {
    val f = new File(s"$testDataDir/$path")
    new String(Files.readAllBytes(f.toPath), UTF_8)
  }

  lazy val baseRepo = {
    val dirStr = Option(System.getenv("COURSIER_TESTS_METADATA_DIR")).getOrElse {
      sys.error("COURSIER_TESTS_METADATA_DIR not set")
    }
    val dir = Paths.get(dirStr)
    assert(Files.isDirectory(dir))
    assert(
      Files.isDirectory(dir),
      s"we're in ${Paths.get(".").toAbsolutePath.normalize}, no $dir here"
    )
    dir
  }

  val updateSnapshots = Option(System.getenv("FETCH_MOCK_DATA"))
    .exists(s => s == "1" || s == "true")

  def artifact[F[_]: Sync]: Repository.Fetch[F] =
    MockCache.create[F](baseRepo, writeMissing = updateSnapshots, pool = pool).fetch
  def artifactWithProxy[F[_]: Sync](proxy: java.net.Proxy): Repository.Fetch[F] =
    MockCache.create[F](baseRepo, writeMissing = updateSnapshots, pool = pool).withProxy(
      Some(proxy)
    ).fetch

  val taskArtifact = artifact[Task]

  private lazy val baseResources = {
    val dir = Paths.get(testDataDir)
    assert(Files.isDirectory(dir))
    dir
  }

  def tryCreate(path: String, content: String): Unit =
    if (updateSnapshots) {
      val path0 = baseResources.resolve(path)
      Util.createDirectories(path0.getParent)
      Files.write(path0, content.getBytes(UTF_8))
    }

}
