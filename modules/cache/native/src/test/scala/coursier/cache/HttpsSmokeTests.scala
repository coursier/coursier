package coursier.cache

import coursier.cache.TestUtil._
import coursier.util.{Artifact, Task}
import utest._

/** End-to-end check of the TLS layer, against a real CA-signed host.
  *
  * Everything else in the Native suite talks to [[RawHttpServer]] over plain HTTP, which leaves the
  * OpenSSL bindings in `Tls.scala` - the handshake, SNI, and verification against the system CA
  * store - untested. Hitting Maven Central is the same thing `RemoteCacheTests` does on the JVM.
  */
object HttpsSmokeTests extends TestSuite {

  private val pomUrl =
    "https://repo1.maven.org/maven2/io/get-coursier/coursier-core_2.13/2.1.24/" +
      "coursier-core_2.13-2.1.24.pom"

  val tests = Tests {
    test("fetch over https, checksum included") {
      withTmpDir { dir =>
        val cache = FileCache[Task]((dir / "cache").toIO)
        val res   = cache.file(Artifact(pomUrl)).run.unsafeRun(wrapExceptions = true)(cache.ec)
        val f     = res.fold(e => throw new Exception(e.describe), identity)
        // the default checksums are SHA-1, so getting here means the digest matched too
        assert(os.read(os.Path(f)).contains("<artifactId>coursier-core_2.13</artifactId>"))
      }
    }
  }
}
