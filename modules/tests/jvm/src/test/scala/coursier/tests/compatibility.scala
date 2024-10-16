package coursier.tests

import java.io.{File, FileInputStream}
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

  def textResource(path: String)(implicit ec: ExecutionContext): Future[String] = Future {
    val f                    = new File("modules/tests/shared/src/test/resources/" + path)
    var is0: FileInputStream = null
    try {
      is0 = new FileInputStream(f)
      new String(Platform.readFullySync(is0), UTF_8)
    }
    finally if (is0 != null)
        is0.close()
  }

  private val baseRepo = {
    val dir = Paths.get("modules/tests/metadata")
    assert(
      Files.isDirectory(dir),
      s"we're in ${Paths.get(".").toAbsolutePath.normalize}, no $dir here"
    )
    dir
  }

  private val updateSnapshots = Option(System.getenv("FETCH_MOCK_DATA"))
    .exists(s => s == "1" || s == "true")

  def artifact[F[_]: Sync]: Repository.Fetch[F] =
    MockCache.create[F](baseRepo, writeMissing = updateSnapshots, pool = pool).fetch
  def artifactWithProxy[F[_]: Sync](proxy: java.net.Proxy): Repository.Fetch[F] =
    MockCache.create[F](baseRepo, writeMissing = updateSnapshots, pool = pool).withProxy(
      Some(proxy)
    ).fetch

  val taskArtifact = artifact[Task]

  private lazy val baseResources = {
    val dir = Paths.get("modules/tests/shared/src/test/resources")
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
