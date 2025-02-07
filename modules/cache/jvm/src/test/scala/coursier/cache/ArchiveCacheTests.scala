package coursier.cache

import coursier.cache.TestUtil._
import coursier.util.{Artifact, Task}
import utest._

import java.io.File

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object ArchiveCacheTests extends TestSuite {

  val tests = Tests {
    test("jar") {
      withTmpDir { dir =>
        val cache = FileCache()
        val archiveCache = ArchiveCache[Task](
          location = (dir / "arc").toIO,
          cache = cache,
          unArchiver = UnArchiver.default()
        )

        val future = archiveCache
          .get(Artifact(
            "https://repo1.maven.org/maven2/org/fusesource/jansi/jansi/2.4.1/jansi-2.4.1.jar"
          ))
          .future()(cache.ec)
        val jarDir = Await.result(future, Duration.Inf).toTry.get
        val file =
          new File(jarDir, "org/fusesource/jansi/internal/native/Mac/arm64/libjansi.jnilib")
        assert(file.exists())
      }
    }
  }

}
