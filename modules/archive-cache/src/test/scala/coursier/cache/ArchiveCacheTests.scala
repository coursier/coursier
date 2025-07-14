package coursier.cache

import coursier.cache.TestUtil._
import coursier.core.Authentication
import coursier.util.{Artifact, Task}
import utest._

import java.io.File

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Properties

abstract class ArchiveCacheTests extends TestSuite {

  def sandboxedCache = coursier.cache.FileCache[Task]((os.pwd / "test-cache").toIO)
  def archiveCache(location: os.Path): ArchiveCache[Task] =
    ArchiveCache[Task](location.toIO)
  // Uncomment this to re-download everything in a test run
  // .withCache(sandboxedCache)

  def notOnWindows: Boolean = false

  def checkArchiveHas(archiveUrl: String, pathInArchive: os.SubPath): Unit =
    checkArchiveHas(Artifact(archiveUrl), pathInArchive)

  def checkArchiveHas(
    archiveUrl: String,
    pathInArchiveOpt: Option[os.SubPath] = None,
    withFileOpt: Option[File => Unit] = None
  ): Unit =
    checkArchiveHas(Artifact(archiveUrl), pathInArchiveOpt, None)

  def checkArchiveHas(archive: Artifact, pathInArchive: os.SubPath): Unit =
    checkArchiveHas(archive, Some(pathInArchive), None)

  def checkArchiveHas(
    archive: Artifact,
    pathInArchiveOpt: Option[os.SubPath],
    withFileOpt: Option[File => Unit]
  ): Unit =
    withTmpDir { dir =>
      val archiveCache0 = archiveCache(dir / "arc")

      val future = archiveCache0
        .get(archive)
        .future()(archiveCache0.cache.ec)
      val archiveDir = Await.result(future, Duration.Inf).toTry.get
      pathInArchiveOpt match {
        case None                => archiveDir
        case Some(pathInArchive) =>
          val file = new File(archiveDir, pathInArchive.toString)
          assert(file.exists())
          assert(file.isFile())
          for (withFile <- withFileOpt)
            withFile(file)
      }
    }

  def actualTests = Tests {
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
        "https://github.com/VirtusLab/scala-cli/releases/download/v1.7.1/scala-cli-x86_64-pc-linux.deb",
        os.sub / "control.tar.zst"
      )
    }

    test("xz") {
      checkArchiveHas(
        "https://github.com/xz-mirror/xz/raw/refs/heads/master/tests/files/good-1-check-sha256.xz",
        os.sub
      )
    }

    test("detect tgz") {

      val repoName = "library/hello-world"
      val auth     = Authentication.byNameBearerToken(
        DockerTestUtil.token(repoName)
      )

      checkArchiveHas(
        Artifact(
          s"https://registry-1.docker.io/v2/$repoName/blobs/sha256:c9c5fd25a1bdc181cb012bc4fbb1ab272a975728f54064b7ae3ee8e77fd28c46"
        )
          .withAuthentication(Some(auth)),
        os.sub / "hello"
      )
    }

    test("archive in archive") {
      checkArchiveHas(
        "https://github.com/VirtusLab/scala-cli/releases/download/v1.7.1/scala-cli-x86_64-pc-linux.deb!data.tar.zst!usr/bin/scala-cli"
      )

      // TODO Add that back after having factored some Debian index related helpers from QemuFiles,
      // so that we can get from the index the latest URL of the package. Hard-coded addresses tend
      // to disappear after some time.
      // checkArchiveHas(
      //   "https://ftp.debian.org/debian/pool/main/h/hello/hello_2.10-3+b1_arm64.deb!data.tar.xz!usr/share/doc/hello/changelog.gz!",
      //   withFileOpt = Some { changelog =>
      //     val content = os.read(os.Path(changelog))
      //     assert(content.startsWith("2014-11-16  Sami Kerola  <kerolasa@iki.fi>"))
      //   }
      // )
    }

    test("short path dir") {
      val archive =
        Artifact("https://repo1.maven.org/maven2/org/fusesource/jansi/jansi/2.4.1/jansi-2.4.1.jar")
      val pathInArchive = os.sub / "org/fusesource/jansi/internal/native/Mac/arm64/libjansi.jnilib"
      withTmpDir { dir =>
        val defaultDir = dir / "arc"
        val shortBase  = dir / "short"

        def check(useShortBase: Boolean): Unit = {
          val archiveCache0 = archiveCache(defaultDir)
            .withShortPathDirectory(if (useShortBase) Some(shortBase.toIO) else None)

          val future = archiveCache0
            .get(archive)
            .future()(archiveCache0.cache.ec)
          val archiveDir = Await.result(future, Duration.Inf).toTry.get
          val file       = new File(archiveDir, pathInArchive.toString)
          assert(file.exists())
          assert(file.isFile())

          if (useShortBase) {
            assert(archiveDir.toPath.startsWith(shortBase.toNIO))
            assert(!archiveDir.toPath.startsWith(defaultDir.toNIO))
          }
          else {
            assert(!archiveDir.toPath.startsWith(shortBase.toNIO))
            assert(archiveDir.toPath.startsWith(defaultDir.toNIO))
          }

          val subPath = os.Path(archiveDir)
            .relativeTo(if (useShortBase) shortBase else defaultDir)
            .asSubPath
          if (useShortBase)
            assert(subPath.segments.length == 1) // checksum should be the only component
          else
            assert(subPath.segments.length > 1)
        }

        check(useShortBase = true)
        check(useShortBase = false)
      }
    }
  }

  val tests =
    if (notOnWindows && Properties.isWin)
      Tests {}
    else
      actualTests

}

object ArchiveCacheTests extends ArchiveCacheTests
