package coursier

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.security.MessageDigest
import java.util.Locale

import coursier.cache.{Cache, MockCache}
import coursier.paths.Util
import coursier.util.{Sync, Task}

import scala.concurrent.{ExecutionContext, Future}

abstract class PlatformTestHelpers {

  private lazy val pool = Sync.fixedThreadPool(6)

  private val mockDataLocation = {
    val dir = Paths.get("modules/tests/metadata")
    assert(Files.isDirectory(dir))
    dir
  }

  val handmadeMetadataLocation = {
    val dir = Paths.get("modules/tests/handmade-metadata/data")
    assert(Files.isDirectory(dir))
    dir
  }

  val handmadeMetadataBase = handmadeMetadataLocation
    .toAbsolutePath
    .toFile // .toFile.toURI gives file:/ URIs, whereas .toUri gives file:/// (the former appears in some test fixtures now)
    .toURI
    .toASCIIString
    .stripSuffix("/") + "/"

  val writeMockData = Option(System.getenv("FETCH_MOCK_DATA"))
    .exists(s => s == "1" || s.toLowerCase(Locale.ROOT) == "true")

  val cache: Cache[Task] =
    MockCache.create[Task](mockDataLocation, pool = pool, writeMissing = writeMockData)
      .withDummyArtifact(_.url.endsWith(".jar"))

  val handmadeMetadataCache: Cache[Task] =
    MockCache.create[Task](handmadeMetadataLocation, pool = pool)

  val cacheWithHandmadeMetadata: Cache[Task] =
    MockCache.create[Task](mockDataLocation, pool = pool, Seq(handmadeMetadataLocation), writeMissing = writeMockData)
      .withDummyArtifact(_.url.endsWith(".jar"))

  def textResource(path: String)(implicit ec: ExecutionContext): Future[String] =
    Future {
      val p = Paths.get(path)
      val b = Files.readAllBytes(p)
      new String(b, StandardCharsets.UTF_8)
    }

  def maybeWriteTextResource(path: String, content: String): Unit = {
    val p = Paths.get(path)
    Util.createDirectories(p.getParent)
    Files.write(p, content.getBytes(StandardCharsets.UTF_8))
  }

  def sha1(s: String): String = {
    val md = MessageDigest.getInstance("SHA-1")
    val b = md.digest(s.getBytes(StandardCharsets.UTF_8))
    new BigInteger(1, b).toString(16)
  }

}
