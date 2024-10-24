package coursier.tests

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.security.MessageDigest
import java.util.Locale

import com.github.difflib.{DiffUtils, UnifiedDiffUtils}
import coursier.cache.{Cache, MockCache}
import coursier.paths.Util
import coursier.util.{Sync, Task}

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._

abstract class PlatformTestHelpers {

  lazy val testDataDir =
    Option(System.getenv("COURSIER_TEST_DATA_DIR")).getOrElse {
      sys.error("COURSIER_TEST_DATA_DIR env var not set")
    }

  private lazy val pool = Sync.fixedThreadPool(6)

  private val mockDataLocation = {
    val dirStr = Option(System.getenv("COURSIER_TESTS_METADATA_DIR")).getOrElse {
      sys.error("COURSIER_TESTS_METADATA_DIR not set")
    }
    val dir = Paths.get(dirStr)
    assert(Files.isDirectory(dir))
    dir
  }

  val handmadeMetadataLocation = {
    val dirStr = Option(System.getenv("COURSIER_TESTS_HANDMADE_METADATA_DIR")).getOrElse {
      sys.error("COURSIER_TESTS_HANDMADE_METADATA_DIR not set")
    }
    val dir = Paths.get(dirStr)
    assert(Files.isDirectory(dir))
    dir
  }

  val handmadeMetadataBase = handmadeMetadataLocation
    .toAbsolutePath
    .toFile // .toFile.toURI gives file:/ URIs, whereas .toUri gives file:/// (the former appears in some test fixtures now)
    .toURI
    .toASCIIString
    .stripSuffix("/") + "/"

  val updateSnapshots = Option(System.getenv("FETCH_MOCK_DATA"))
    .exists(s => s == "1" || s.toLowerCase(Locale.ROOT) == "true")

  val cache: Cache[Task] =
    MockCache.create[Task](mockDataLocation, pool = pool, writeMissing = updateSnapshots)
      .withDummyArtifact(a => a.url.endsWith(".jar") || a.url.endsWith(".klib"))

  val handmadeMetadataCache: Cache[Task] =
    MockCache.create[Task](handmadeMetadataLocation, pool = pool)

  val cacheWithHandmadeMetadata: Cache[Task] =
    MockCache.create[Task](
      mockDataLocation,
      pool = pool,
      Seq(handmadeMetadataLocation),
      writeMissing = updateSnapshots
    )
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
    val b  = md.digest(s.getBytes(StandardCharsets.UTF_8))
    new BigInteger(1, b).toString(16)
  }

  def maybePrintConsistencyDiff(fromOrdered: Seq[String], fromMinimized: Seq[String]): Unit = {
    val patch = DiffUtils.diff(fromOrdered.asJava, fromMinimized.asJava)
    val diff = UnifiedDiffUtils.generateUnifiedDiff(
      "ordered-dependencies",
      "minimized-dependencies",
      fromOrdered.asJava,
      patch,
      3
    )
    for (l <- diff.asScala) {
      val colorOpt =
        if (l.startsWith("@")) Some(Console.BLUE)
        else if (l.startsWith("-")) Some(Console.RED)
        else if (l.startsWith("+")) Some(Console.GREEN)
        else None
      System.err.println(
        colorOpt.getOrElse("") + l + colorOpt.map(_ => Console.RESET).getOrElse("")
      )
    }
  }
}
