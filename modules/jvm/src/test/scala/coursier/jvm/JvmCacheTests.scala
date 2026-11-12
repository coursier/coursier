package coursier.jvm

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}

import coursier.cache.internal.FileUtil
import coursier.cache.{ArchiveCache, Cache, FileCache, MockCache}
import coursier.util.{Sync, Task}
import utest._

import scala.sys.process._
import java.io.IOException
import scala.util.Success
import scala.util.Failure
import scala.util.Properties
import scala.util.Try

object JvmCacheTests extends TestSuite {

  val theOS    = if (Properties.isWin) "windows" else "the-os"
  val filename = if (Properties.isWin) "java.bat" else "java"

  private def deleteRecursive(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles().foreach(deleteRecursive)
    f.delete()
  }

  def withTempDir[T](f: Path => T): T = {
    var dir: Path = null
    try {
      dir = Files.createTempDirectory("jvm-cache-tests-")
      f(dir)
    }
    finally if (dir != null)
        deleteRecursive(dir.toFile)
  }

  def withTempDir0[T](f: os.Path => T): T = {
    var dir: os.Path = null
    try {
      dir = os.temp.dir(prefix = "jvm-cache-tests-")
      f(dir)
    }
    finally if (dir != null)
        deleteRecursive(dir.toIO)
  }

  private val poolInitialized = new AtomicBoolean(false)
  private lazy val pool = {
    val p = Sync.fixedThreadPool(6)
    poolInitialized.set(true)
    p
  }

  override def utestAfterAll(): Unit =
    if (poolInitialized.getAndSet(false))
      pool.shutdown()

  val mockDataLocation = {
    val dir = Option(System.getenv("COURSIER_JVM_TESTS_MOCK_CACHE"))
      .map(Paths.get(_))
      .getOrElse {
        sys.error("COURSIER_JVM_TESTS_MOCK_CACHE not set")
      }
    assert(Files.isDirectory(dir))
    dir
  }

  private val strIndex =
    """{
      |  "the-os": {
      |    "the-arch": {
      |      "jdk@the-jdk": {
      |        "1.1": "tgz+https://foo.com/download/the-jdk-1.1.tar.gz",
      |        "1.2": "tgz+https://foo.com/download/the-jdk-1.2.tar.gz"
      |      }
      |    }
      |  },
      |  "darwin": {
      |    "the-arch": {
      |      "jdk@the-jdk": {
      |        "1.1": "tgz+https://foo.com/download/the-jdk-1.1-macos.tar.gz",
      |        "1.2": "tgz+https://foo.com/download/the-jdk-1.2.tar.gz"
      |      }
      |    }
      |  },
      |  "windows": {
      |    "the-arch": {
      |      "jdk@the-jdk": {
      |        "1.1": "tgz+https://foo.com/download/the-jdk-1.1-windows.tar.gz",
      |        "1.2": "tgz+https://foo.com/download/the-jdk-1.2-windows.tar.gz"
      |      }
      |    }
      |  }
      |}
      |""".stripMargin
  private val index = JvmIndex.fromString(strIndex).fold(throw _, identity)

  private val cache = MockCache.create[Task](
    mockDataLocation,
    pool,
    baseChangingOpt = Some(mockDataLocation)
  )

  val tests = Tests {
    test("specific version") {
      withTempDir { tmpDir =>
        val archiveCache = ArchiveCache.create[Task](tmpDir.toFile).copy(cache = cache)
        val jvmCache = JvmCache()
          .copy(
            archiveCache = archiveCache,
            os = theOS,
            architecture = "the-arch",
            defaultJdkNameOpt = None,
            defaultVersionOpt = None
          )
          .withIndex(Task.point(index))

        val home           = jvmCache.get("the-jdk:1.1").unsafeRun(wrapExceptions = true)(cache.ec)
        val expectedOutput = "the jdk 1.1\n"
        val javaExec       = new File(new File(home, "bin"), filename)

        val output = Seq(javaExec.getAbsolutePath, "-version").!!
        assert(output.replace("\r\n", "\n") == expectedOutput)
      }
    }

    test("index loaded once") {
      // https://github.com/coursier/coursier/issues/3764
      withTempDir { tmpDir =>
        val archiveCache = ArchiveCache.create[Task](tmpDir.toFile).copy(cache = cache)
        val loadCount    = new AtomicInteger
        val jvmCache = JvmCache()
          .copy(
            archiveCache = archiveCache,
            os = theOS,
            architecture = "the-arch",
            defaultJdkNameOpt = None,
            defaultVersionOpt = None
          )
          .withIndex(Task.delay {
            loadCount.incrementAndGet()
            index
          })

        assert(loadCount.get() == 0)

        val ids = Seq("the-jdk:1.1", "the-jdk:1.2", "the-jdk:1+")
        val installed = Task.gather
          .gather(ids.map(jvmCache.getIfInstalled(_)))
          .unsafeRun(wrapExceptions = true)(cache.ec)
        assert(installed.forall(_.isEmpty))
        assert(loadCount.get() == 1)

        val entries = jvmCache.entries("the-jdk:1.1").unsafeRun(wrapExceptions = true)(cache.ec)
        assert(entries.exists(_.nonEmpty))
        assert(loadCount.get() == 1)

        jvmCache.get("the-jdk:1.1").unsafeRun(wrapExceptions = true)(cache.ec)
        assert(loadCount.get() == 1)
      }
    }

    test("version range") {
      withTempDir { tmpDir =>
        val archiveCache = ArchiveCache.create[Task](tmpDir.toFile).copy(cache = cache)
        val jvmCache = JvmCache()
          .copy(
            archiveCache = archiveCache,
            os = theOS,
            architecture = "the-arch",
            defaultJdkNameOpt = None,
            defaultVersionOpt = None
          )
          .withIndex(Task.point(index))

        val home           = jvmCache.get("the-jdk:1+").unsafeRun(wrapExceptions = true)(cache.ec)
        val javaExec       = new File(new File(home, "bin"), filename)
        val output         = Seq(javaExec.getAbsolutePath, "-version").!!
        val expectedOutput = "the jdk 1.2\n"
        assert(output.replace("\r\n", "\n") == expectedOutput)
      }
    }

    test("Contents/Home directory on macOS") {
      withTempDir { tmpDir =>
        val archiveCache = ArchiveCache.create[Task](tmpDir.toFile).copy(cache = cache)
        val jvmCache = JvmCache()
          .copy(
            archiveCache = archiveCache,
            os = "darwin",
            architecture = "the-arch",
            defaultJdkNameOpt = None,
            defaultVersionOpt = None
          )
          .withIndex(Task.point(index))

        val home = jvmCache.get("the-jdk:1.1").unsafeRun(wrapExceptions = true)(cache.ec)
        assert(home.getName == "Home")
        assert(home.getParentFile.getName == "Contents")
        val javaExec = new File(home, "bin/java")
        try {
          val output         = Seq(javaExec.getAbsolutePath, "-version").!!
          val expectedOutput = "the jdk 1.1\n"
          assert(output == expectedOutput)
          ()
        }
        catch {
          case _: IOException if Properties.isWin => ()
        }

        val alreadyThereHome = jvmCache
          .getIfInstalled("the-jdk:1.1")
          .unsafeRun(wrapExceptions = true)(cache.ec)
          .getOrElse {
            sys.error("Should have been there")
          }
        assert(alreadyThereHome.getName == "Home")
        assert(alreadyThereHome.getParentFile.getName == "Contents")
        val alreadyThereJavaExec = new File(alreadyThereHome, "bin/java")
        try {
          val output         = Seq(alreadyThereJavaExec.getAbsolutePath, "-version").!!
          val expectedOutput = "the jdk 1.1\n"
          assert(output == expectedOutput)
          ()
        }
        catch {
          case _: IOException if Properties.isWin => ()
        }
      }
    }

    test("no Contents/Home directory on macOS") {
      withTempDir { tmpDir =>
        val archiveCache = ArchiveCache.create[Task](tmpDir.toFile).copy(cache = cache)
        val jvmCache = JvmCache()
          .copy(
            archiveCache = archiveCache,
            os = "darwin",
            architecture = "the-arch",
            defaultJdkNameOpt = None,
            defaultVersionOpt = None
          )
          .withIndex(Task.point(index))

        val home = jvmCache.get("the-jdk:1.2").unsafeRun(wrapExceptions = true)(cache.ec)
        assert(home.getName == "the-jdk-1.2")
        val javaExec = new File(home, "bin/java")
        try {
          val output         = Seq(javaExec.getAbsolutePath, "-version").!!
          val expectedOutput = "the jdk 1.2\n"
          assert(output == expectedOutput)
          ()
        }
        catch {
          case _: IOException if Properties.isWin => ()
        }
      }
    }

    test("URL id") {
      withTempDir0 { tmpDir =>
        val archiveCache = ArchiveCache.create[Task](tmpDir.toIO).copy(cache = cache)
        val jvmCache = JvmCache()
          .copy(
            archiveCache = archiveCache,
            os = "the-os",
            architecture = "the-arch",
            defaultJdkNameOpt = None,
            defaultVersionOpt = None
          )

        val dir = os.Path(
          jvmCache
            .get("https://foo.com/download/the-jdk-1.2.tar.gz")
            .unsafeRun(wrapExceptions = true)(cache.ec)
        )

        val expectedDir =
          os.rel / "https" / "foo.com" / "download" / "the-jdk-1.2.tar.gz" / "the-jdk-1.2"
        val relDir = dir.relativeTo(tmpDir)

        assert(relDir == expectedDir)
      }
    }

    test("checksums") {
      val archiveName =
        if (Properties.isWin) "the-jdk-1.1-windows.tar.gz" else "the-jdk-1.1.tar.gz"
      val archiveSha256 = {
        val archive = os.Path(mockDataLocation) / "https" / "foo.com" / "download" / archiveName
        val md      = java.security.MessageDigest.getInstance("SHA-256")
        md.digest(os.read.bytes(archive)).map(b => f"$b%02x").mkString
      }

      def jvmCacheWithChecksums(
        tmpDir: Path,
        checksums: String,
        url: String = s"https://foo.com/download/$archiveName",
        cache0: Cache[Task] = cache
      ): JvmCache = {
        val strV2Index =
          s"""{
             |  "the-jdk": {
             |    "1.1": {
             |      "url": "$url",
             |      "checksums": $checksums
             |    }
             |  }
             |}
             |""".stripMargin
        val v2Index = JvmIndex
          .fromBytes(strV2Index.getBytes(StandardCharsets.UTF_8), theOS, "the-arch")
          .fold(throw _, identity)
        val archiveCache = ArchiveCache.create[Task](tmpDir.toFile).copy(cache = cache0)
        JvmCache()
          .copy(
            archiveCache = archiveCache,
            os = theOS,
            architecture = "the-arch",
            defaultJdkNameOpt = None,
            defaultVersionOpt = None
          )
          .withIndex(Task.point(v2Index))
      }

      def checkJdk(home: File): Unit = {
        val javaExec = new File(new File(home, "bin"), filename)
        val output   = Seq(javaExec.getAbsolutePath, "-version").!!
        assert(output.replace("\r\n", "\n") == "the jdk 1.1\n")
      }

      test("valid") {
        withTempDir { tmpDir =>
          val jvmCache = jvmCacheWithChecksums(tmpDir, s"""{"sha256": "$archiveSha256"}""")
          val entries  = jvmCache.entries("the-jdk:1.1").unsafeRun(wrapExceptions = true)(cache.ec)
          assert(entries.map(_.map(_.checksums)) == Right(Seq(Map("sha256" -> archiveSha256))))
          checkJdk(jvmCache.get("the-jdk:1.1").unsafeRun(wrapExceptions = true)(cache.ec))
        }
      }

      test("invalid") {
        withTempDir { tmpDir =>
          val wrongSha256 = archiveSha256.reverse
          val jvmCache    = jvmCacheWithChecksums(tmpDir, s"""{"sha256": "$wrongSha256"}""")
          val res = Try(jvmCache.get("the-jdk:1.1").unsafeRun(wrapExceptions = true)(cache.ec))
          val wrongChecksumOpt = res match {
            case Failure(ex) =>
              Iterator.iterate[Throwable](ex)(_.getCause)
                .takeWhile(_ != null)
                .collectFirst { case e: JvmCache.WrongArchiveChecksum => e }
            case Success(_) => None
          }
          assert(wrongChecksumOpt.map(_.expected) == Some(wrongSha256))
          assert(wrongChecksumOpt.map(_.actual) == Some(archiveSha256))

          // the archive mustn't have been extracted
          val installed =
            jvmCache.getIfInstalled("the-jdk:1.1").unsafeRun(wrapExceptions = true)(cache.ec)
          assert(installed.isEmpty)
        }
      }

      test("strongest checksum is checked") {
        withTempDir { tmpDir =>
          val jvmCache = jvmCacheWithChecksums(
            tmpDir,
            s"""{"md5": "wrong", "sha256": "$archiveSha256"}"""
          )
          checkJdk(jvmCache.get("the-jdk:1.1").unsafeRun(wrapExceptions = true)(cache.ec))
        }
      }

      test("digest persisted in FileCache") {
        withTempDir0 { tmpDir =>
          val archive = tmpDir / "src" / archiveName
          os.copy(
            os.Path(mockDataLocation) / "https" / "foo.com" / "download" / archiveName,
            archive,
            createFolders = true
          )
          val url = archive.toNIO.toUri.toASCIIString
          val fileCache = FileCache.create[Task]()
            .copy(location = (tmpDir / "cache").toIO, localArtifactsShouldBeCached = true)
          val cachedArchive = fileCache.localFile(url)
          val digestFile    = FileCache.auxiliaryFile(cachedArchive, "SHA-256.computed")

          def jvmCache(sha256: String) = jvmCacheWithChecksums(
            (tmpDir / "arc").toNIO,
            s"""{"sha256": "$sha256"}""",
            url = url,
            cache0 = fileCache
          )
          def digestFileContent(): String =
            os.read.bytes(os.Path(digestFile)).map(b => f"$b%02x").mkString

          checkJdk(jvmCache(archiveSha256).get("the-jdk:1.1").unsafeRun(wrapExceptions = true)(
            fileCache.ec
          ))
          assert(digestFile.exists())
          assert(digestFileContent() == archiveSha256)

          // the persisted digest is used rather than computed again: with a fake one matching
          // the index, the check passes
          val fakeSha256 = "ab" * 32
          os.write.over(
            os.Path(digestFile),
            fakeSha256.grouped(2).map(Integer.parseInt(_, 16).toByte).toArray[Byte]
          )
          os.remove.all(tmpDir / "arc")
          checkJdk(jvmCache(fakeSha256).get("the-jdk:1.1").unsafeRun(wrapExceptions = true)(
            fileCache.ec
          ))
        }
      }

      test("archive with wrong checksum downloaded again by FileCache") {
        withTempDir0 { tmpDir =>
          val archive = tmpDir / "src" / archiveName
          os.copy(
            os.Path(mockDataLocation) / "https" / "foo.com" / "download" / archiveName,
            archive,
            createFolders = true
          )
          val url = archive.toNIO.toUri.toASCIIString
          val fileCache = FileCache.create[Task]()
            .copy(location = (tmpDir / "cache").toIO, localArtifactsShouldBeCached = true)
          val cachedArchive = os.Path(fileCache.localFile(url))
          val digestFile = os.Path(FileCache.auxiliaryFile(cachedArchive.toIO, "SHA-256.computed"))

          val jvmCache = jvmCacheWithChecksums(
            (tmpDir / "arc").toNIO,
            s"""{"sha256": "$archiveSha256"}""",
            url = url,
            cache0 = fileCache
          )
          checkJdk(jvmCache.get("the-jdk:1.1").unsafeRun(wrapExceptions = true)(fileCache.ec))

          // make the cached archive look like it has a wrong checksum (an archive that's not
          // corrupted, but isn't the one of the index): it should be downloaded again
          val wrongDigest = ("ab" * 32).grouped(2).map(Integer.parseInt(_, 16).toByte).toArray[Byte]
          os.write.over(digestFile, wrongDigest)
          os.remove.all(tmpDir / "arc")
          checkJdk(jvmCache.get("the-jdk:1.1").unsafeRun(wrapExceptions = true)(fileCache.ec))
          assert(os.read.bytes(digestFile).toSeq != wrongDigest.toSeq)

          // still wrong after downloading it again
          os.write.over(archive, "corrupted")
          os.remove(cachedArchive)
          FileCache.clearAuxiliaryFiles(cachedArchive.toIO)
          os.remove.all(tmpDir / "arc")
          val res = Try(jvmCache.get("the-jdk:1.1").unsafeRun(wrapExceptions = true)(fileCache.ec))
          val wrongChecksumOpt = res match {
            case Failure(ex) =>
              Iterator.iterate[Throwable](ex)(_.getCause)
                .takeWhile(_ != null)
                .collectFirst { case e: JvmCache.WrongArchiveChecksum => e }
            case Success(_) => None
          }
          assert(wrongChecksumOpt.map(_.expected) == Some(archiveSha256))
          assert(wrongChecksumOpt.map(_.archive) == Some(cachedArchive.toIO))
        }
      }

      test("unknown checksum types are ignored") {
        withTempDir { tmpDir =>
          val jvmCache = jvmCacheWithChecksums(tmpDir, """{"sha3-256": "foo"}""")
          checkJdk(jvmCache.get("the-jdk:1.1").unsafeRun(wrapExceptions = true)(cache.ec))
        }
      }
    }

  }
}
