package coursier.cache

import coursier.cache.TestUtil._
import coursier.util.{Artifact, Task}
import utest._

import java.io.File

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object ArchiveCacheTests extends TestSuite {

  def checkArchiveHas(archiveUrl: String, pathInArchive: os.SubPath): Unit =
    withTmpDir { dir =>
      val archiveCache = ArchiveCache[Task]((dir / "arc").toIO)

      val future = archiveCache
        .get(Artifact(archiveUrl))
        .future()(archiveCache.cache.ec)
      val archiveDir = Await.result(future, Duration.Inf).toTry.get
      val file       = new File(archiveDir, pathInArchive.toString)
      assert(file.exists())
      assert(file.isFile())
    }

  val tests = Tests {
    test("jar") {
      checkArchiveHas(
        "https://repo1.maven.org/maven2/org/fusesource/jansi/jansi/2.4.1/jansi-2.4.1.jar",
        os.sub / "org/fusesource/jansi/internal/native/Mac/arm64/libjansi.jnilib"
      )
    }

    test("txz") {
      checkArchiveHas(
        "https://ftp.gnu.org/gnu/hello/hello-2.7.tar.xz",
        os.sub / "hello-2.7/src/hello.c"
      )
    }

    test("txz") {
      checkArchiveHas(
        "https://europe.mirror.pkgbuild.com/extra/os/x86_64/busybox-1.36.1-2-x86_64.pkg.tar.zst",
        os.sub / "usr/bin/busybox"
      )
    }

    test("deb") {
      checkArchiveHas(
        "https://ftp.debian.org/debian/pool/main/h/hello/hello_2.10-3+b1_arm64.deb",
        os.sub / "control.tar.xz"
      )
    }

    test("xz") {
      checkArchiveHas(
        "https://github.com/xz-mirror/xz/raw/refs/heads/master/tests/files/good-1-check-sha256.xz",
        os.sub
      )
    }
  }

}
