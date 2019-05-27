package coursier.cli.install

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.time.Instant
import java.util.Locale
import java.util.zip.ZipFile

import coursier.cache.internal.FileUtil
import coursier.cache.{Cache, CacheDefaults, MockCache}
import coursier.cli.app.RawAppDescriptor
import coursier.util.{Sync, Task}
import org.junit.runner.RunWith
import org.scalatest.{BeforeAndAfterAll, FlatSpec}
import org.scalatestplus.junit.JUnitRunner

import scala.collection.JavaConverters._

@RunWith(classOf[JUnitRunner])
class InstallTests extends FlatSpec with BeforeAndAfterAll {

  private val pool = Sync.fixedThreadPool(CacheDefaults.concurrentDownloadCount)

  private val mockDataLocation = {
    val dir = Paths.get("modules/tests/metadata")
    assert(Files.isDirectory(dir))
    dir
  }

  private val writeMockData = sys.env
    .get("FETCH_MOCK_DATA")
    .exists(s => s == "1" || s.toLowerCase(Locale.ROOT) == "true")

  private val cache: Cache[Task] =
    MockCache.create[Task](mockDataLocation, writeMissing = writeMockData, pool = pool)

  private def delete(d: Path): Unit =
    if (Files.isDirectory(d))
      Files.list(d)
        .iterator()
        .asScala
        .foreach(delete)
    else
      Files.deleteIfExists(d)

  private def withTempDir[T](f: Path => T): T = {
    val tmpDir = Files.createTempDirectory("coursier-install-test")
    try f(tmpDir)
    finally {
      delete(tmpDir)
    }
  }

  override protected def afterAll(): Unit = {
    pool.shutdown()
  }

  it should "generate an echo launcher" in withTempDir { tmpDir =>

    val rawDesc = RawAppDescriptor(
      dependencies = List("io.get-coursier:echo:1.0.2"),
      repositories = List("central")
    )
    val appDesc = rawDesc.appDescriptor.toEither.right.get
    val descRepr = rawDesc.repr.getBytes(StandardCharsets.UTF_8)

    val launcher = tmpDir.resolve("echo")
    val now = Instant.now()

    val created = AppGenerator.createOrUpdate(
      Some((appDesc, descRepr)),
      None,
      cache,
      tmpDir,
      launcher,
      now
    )

    assert(created)
    assert(Files.isRegularFile(launcher))

    val zf = new ZipFile(launcher.toFile)
    val ent = zf.getEntry("coursier/bootstrap/launcher/bootstrap-jar-urls")
    assert(ent != null)

    val urls = new String(FileUtil.readFully(zf.getInputStream(ent)), StandardCharsets.UTF_8)
      .split('\n')
      .filter(_.nonEmpty)
      .toSeq
    val expectedUrls = Seq("https://repo1.maven.org/maven2/io/get-coursier/echo/1.0.2/echo-1.0.2.jar")
    assert(urls == expectedUrls)
  }

  // TODO
  //   should generate an assembly
  //   should generate a standalone launcher
  //   should not update an already up-to-date launcher
  //   should update launcher if a new version is available
  //   should update launcher if the app description changes (change default main class?)
  //   should use found main class if it is found, and ignore default main class in that case
  //   should generate a graalvm native image
  //   should update graalvm native image if a new version is available

}
