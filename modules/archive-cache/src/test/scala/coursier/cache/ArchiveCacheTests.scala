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

    def integrityTest(
      artifact: Artifact,
      expectedElems: Seq[os.SubPath]
    ): Unit =
      withTmpDir { dir =>
        val cache         = FileCache().withLocation((dir / "cache").toIO)
        val archiveCache0 = archiveCache(dir / "arc").withCache(cache)

        val localArchivePath = cache.file(artifact).run.unsafeRun()(cache.ec) match {
          case Left(err) =>
            throw new Exception(err)
          case Right(f) =>
            os.Path(f)
        }

        val size = os.size(localArchivePath)

        // corrupt the archive
        val content = os.read.bytes(localArchivePath)
        os.write.over(
          localArchivePath,
          content.take(content.length / 2) ++
            Array.fill[Byte](10)(0) ++
            content.drop(content.length / 2)
        )
        val corruptedSize = os.size(localArchivePath)
        assert(corruptedSize == size + 10)

        val archiveDir = archiveCache0.get(artifact).unsafeRun()(cache.ec) match {
          case Left(err) =>
            throw new Exception(err)
          case Right(path) =>
            os.Path(path)
        }

        val finalSize = os.size(localArchivePath)
        assert(finalSize == size)

        if (os.isDir(archiveDir)) {
          import scala.math.Ordering.Implicits._
          val elems = os.walk(archiveDir)
            .filter(os.isFile)
            .map(_.relativeTo(archiveDir).asSubPath)
            // not sure why this ordering isn't the default
            .sortBy(_.segments)
          if (elems != expectedElems)
            pprint.err.log(elems)
          assert(expectedElems == elems)
        }
        else
          Nil
      }

    test("zip integrity") {
      integrityTest(
        Artifact(
          "https://repo1.maven.org/maven2/com/lihaoyi/mill-dist/1.0.3/mill-dist-1.0.3-example-androidlib-java-1-hello-world.zip"
        ),
        Seq(
          os.sub / "app/releaseKey.jks",
          os.sub / "app/src/androidTest/java/com/helloworld/app/ExampleInstrumentedTest.java",
          os.sub / "app/src/main/AndroidManifest.xml",
          os.sub / "app/src/main/java/com/helloworld/SampleLogic.java",
          os.sub / "app/src/main/java/com/helloworld/app/MainActivity.java",
          os.sub / "app/src/main/res/values/colors.xml",
          os.sub / "app/src/main/res/values/strings.xml",
          os.sub / "app/src/test/java/com/helloworld/ExampleUnitTest.java",
          os.sub / "build.mill",
          os.sub / "mill",
          os.sub / "mill.bat"
        ).map(os.sub / "mill-dist-1.0.3-example-androidlib-java-1-hello-world" / _)
      )
    }

    test("gzip integrity") {
      integrityTest(
        // random gzip file found on Maven Central
        Artifact(
          "https://repo1.maven.org/maven2/org/danbrough/kotlinxtras/curl/binaries/curlLinuxX64/7_86_0/curlLinuxX64-7_86_0.gz"
        ),
        Nil
      )
    }
  }

  val tests =
    if (notOnWindows && Properties.isWin)
      Tests {}
    else
      actualTests

}

object ArchiveCacheTests extends ArchiveCacheTests
