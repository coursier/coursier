package coursier.cache

import coursier.cache.TestUtil._
import coursier.util.{Artifact, Task}
import org.apache.commons.compress.archivers.ArchiveOutputStream
import org.apache.commons.compress.archivers.ar.{ArArchiveEntry, ArArchiveOutputStream}
import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveOutputStream}
import org.apache.commons.compress.archivers.zip.{ZipArchiveEntry, ZipArchiveOutputStream}
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import utest._

import java.io.{ByteArrayOutputStream, OutputStream}
import java.util.zip.GZIPOutputStream

import scala.util.{Failure, Properties, Success, Try, Using}

object ArchiveCacheLimitsTests extends TestSuite {

  // highly compressible content, a few KB once compressed
  private val bigSize = 4 * 1024 * 1024
  private def zeros   = new Array[Byte](bigSize)

  private def compressed(wrap: OutputStream => OutputStream)(content: Array[Byte]): Array[Byte] = {
    val baos = new ByteArrayOutputStream
    Using.resource(wrap(baos))(_.write(content))
    baos.toByteArray
  }
  private val gzip  = compressed(new GZIPOutputStream(_)) _
  private val bzip2 = compressed(new BZip2CompressorOutputStream(_)) _
  private val zstd  = compressed(new ZstdCompressorOutputStream(_)) _
  private val xz    = compressed(new XZCompressorOutputStream(_)) _

  private def archive[E <: org.apache.commons.compress.archivers.ArchiveEntry](
    create: OutputStream => ArchiveOutputStream[E],
    entry: (String, Int) => E
  )(entries: Seq[(String, Array[Byte])]): Array[Byte] = {
    val baos = new ByteArrayOutputStream
    Using.resource(create(baos)) { aos =>
      for ((name, content) <- entries) {
        aos.putArchiveEntry(entry(name, content.length))
        aos.write(content)
        aos.closeArchiveEntry()
      }
      aos.finish()
    }
    baos.toByteArray
  }
  private val tar = archive[TarArchiveEntry](
    os => new TarArchiveOutputStream(os),
    { (name, size) =>
      val e = new TarArchiveEntry(name)
      e.setSize(size)
      e
    }
  ) _
  private val zip = archive[ZipArchiveEntry](
    os => new ZipArchiveOutputStream(os),
    (name, _) => new ZipArchiveEntry(name)
  ) _
  private val ar = archive[ArArchiveEntry](
    os => new ArArchiveOutputStream(os),
    (name, size) => new ArArchiveEntry(name, size.toLong)
  ) _

  // archive extension, and archive content
  private def multiFileArchives(entries: Seq[(String, Array[Byte])]): Seq[(String, Array[Byte])] =
    Seq(
      "tar"     -> tar(entries),
      "tar.gz"  -> gzip(tar(entries)),
      "tar.bz2" -> bzip2(tar(entries)),
      "tar.zst" -> zstd(tar(entries)),
      "tar.xz"  -> xz(tar(entries)),
      "zip"     -> zip(entries),
      "ar"      -> ar(entries)
    )
  private def singleFileArchives(content: Array[Byte]): Seq[(String, Array[Byte])] =
    Seq(
      "gz" -> gzip(content),
      "xz" -> xz(content)
    )

  private def withProp[T](name: String, value: String)(f: => T): T = {
    val former = sys.props.get(name)
    sys.props(name) = value
    try f
    finally former match {
        case Some(v) => sys.props(name) = v
        case None    => sys.props.remove(name)
      }
  }

  // With cacheArchive, the archive is copied to the cache like a downloaded one, so that
  // ArchiveCache checks its integrity before extracting it
  private def get(
    dir: os.Path,
    ext: String,
    content: Array[Byte],
    maxSize: Long,
    maxEntries: Long,
    unArchiver: (Long, Long) => UnArchiver = UnArchiver.default(_, _),
    cacheArchive: Boolean = false
  ): Try[Either[ArtifactError, os.Path]] =
    get0(dir, ext, content, unArchiver(maxSize, maxEntries), cacheArchive)

  private def get0(
    dir: os.Path,
    ext: String,
    content: Array[Byte],
    unArchiver: UnArchiver,
    cacheArchive: Boolean
  ): Try[Either[ArtifactError, os.Path]] = {
    val cache = FileCache[Task]((dir / "cache").toIO)
      .copy(localArtifactsShouldBeCached = cacheArchive)
    val archiveCache = ArchiveCache.create[Task]((dir / "arc").toIO)
      .copy(cache = cache, unArchiver = unArchiver)
    val archive = dir / "archives" / s"archive.$ext"
    os.write.over(archive, content, createFolders = true)
    Try {
      archiveCache.get(Artifact(archive.toNIO.toUri.toASCIIString))
        .unsafeRun()(archiveCache.cache.ec)
        .map(os.Path(_))
    }
  }

  private def checkRejected(
    archives: Seq[(String, Array[Byte])],
    maxSize: Long,
    maxEntries: Long,
    expectedMessage: String,
    unArchiver: (Long, Long) => UnArchiver = UnArchiver.default(_, _)
  ): Unit =
    for ((ext, content) <- archives)
      withTmpDir { dir =>
        get(dir, ext, content, maxSize, maxEntries, unArchiver) match {
          case Failure(ex: UnArchiver.ExtractionLimitException) =>
            assert(ex.getMessage.contains(expectedMessage))
          case other =>
            throw new Exception(s"Expected extraction of $ext archive to fail, got $other")
        }
        // the partial extraction got removed
        val leftOver = os.walk(dir / "arc").filter(os.isFile(_)).filter(_.last != ".lock")
        assert(leftOver.isEmpty)
      }

  // tar archives, extracted by the tar command with UnArchiver.priviledged
  // no xz archives here either, as org.tukaani:xz isn't on the Scala 3 compile class path
  private def tarArchives(entries: Seq[(String, Array[Byte])]): Seq[(String, Array[Byte])] =
    Seq(
      "tar"     -> tar(entries),
      "tar.gz"  -> gzip(tar(entries)),
      "tar.bz2" -> bzip2(tar(entries)),
      "tar.zst" -> zstd(tar(entries))
    )

  private def priviledged(maxSize: Long, maxEntries: Long): UnArchiver =
    UnArchiver.priviledgedTestMode(maxSize, maxEntries)

  val tests = Tests {

    test("size") {
      test("multi-file archives") {
        checkRejected(
          multiFileArchives(Seq("a.txt" -> Array[Byte](1, 2), "big" -> zeros)),
          maxSize = 1024 * 1024,
          maxEntries = 0,
          expectedMessage = "maximum extracted size"
        )
      }
      test("spread over several entries") {
        checkRejected(
          multiFileArchives((1 to 8).map(n => s"file-$n" -> new Array[Byte](bigSize / 8))),
          maxSize = 1024 * 1024,
          maxEntries = 0,
          expectedMessage = "maximum extracted size"
        )
      }
      test("single-file archives") {
        checkRejected(
          singleFileArchives(zeros),
          maxSize = 1024 * 1024,
          maxEntries = 0,
          expectedMessage = "maximum extracted size"
        )
      }
    }

    test("entries") {
      checkRejected(
        multiFileArchives((1 to 20).map(n => s"file-$n" -> Array[Byte](n.toByte))),
        maxSize = 0,
        maxEntries = 10,
        expectedMessage = "maximum number of entries"
      )
    }

    def withinLimits(cacheArchive: Boolean): Unit = {
      val entries = Seq("a.txt" -> Array[Byte](1, 2), "big" -> zeros)
      for ((ext, content) <- multiFileArchives(entries))
        withTmpDir { dir =>
          val res = get(
            dir,
            ext,
            content,
            maxSize = bigSize + 2,
            maxEntries = 2,
            cacheArchive = cacheArchive
          ).get
          res match {
            case Left(err) => throw new Exception(s"Extracting $ext archive", err)
            case Right(extracted) =>
              assert(os.size(extracted / "big") == bigSize.toLong)
          }
        }
      for ((ext, content) <- singleFileArchives(zeros))
        withTmpDir { dir =>
          val res =
            get(dir, ext, content, maxSize = bigSize, maxEntries = 1, cacheArchive = cacheArchive)
              .get
          res match {
            case Left(err)        => throw new Exception(s"Extracting $ext archive", err)
            case Right(extracted) => assert(os.size(extracted) == bigSize.toLong)
          }
        }
    }

    test("within limits") {
      test("local archives") {
        withinLimits(cacheArchive = false)
      }
      // limits applied by the integrity check too
      test("cached archives") {
        withinLimits(cacheArchive = true)
      }
    }

    test("integrity check") {
      // archives that ArchiveCache checks the integrity of
      def integrityChecked(archives: Seq[(String, Array[Byte])]): Seq[(String, Array[Byte])] =
        archives.filter { case (ext, _) => ext == "zip" || ext == "gz" || ext == "tar.gz" }

      // the integrity check applies the default limits, whatever the limits of the UnArchiver
      // of ArchiveCache, so that a cache server checking archives applies them too
      val failing: UnArchiver = new UnArchiver {
        def extract(
          archiveType: ArchiveType,
          archive: java.io.File,
          destDir: java.io.File,
          overwrite: Boolean
        ): Unit =
          throw new Exception("Archive shouldn't be extracted")
      }

      def checkRejectedEarly(
        archives: Seq[(String, Array[Byte])],
        expectedMessage: String
      ): Unit = {
        assert(archives.nonEmpty)
        for ((ext, content) <- archives)
          withTmpDir { dir =>
            get0(dir, ext, content, failing, cacheArchive = true) match {
              case Success(Left(err)) =>
                // the limit is hit while decompressing the archive to check its integrity,
                // before extraction starts
                assert(err.getMessage.contains("to check its integrity"))
                assert(err.getMessage.contains(expectedMessage))
              case other =>
                throw new Exception(s"Expected integrity check of $ext archive to fail, got $other")
            }
            // the downloaded archive isn't deemed corrupted, deleted, and downloaded again
            val cached   = os.walk(dir / "cache").filter(os.isFile(_))
            val archives = cached.filter(_.last == s"archive.$ext")
            assert(archives.length == 1)
            assert(os.read.bytes(archives.head).sameElements(content))
            assert(!cached.exists(_.last.endsWith("__integrity")))
            // nothing got extracted
            val extracted =
              if (os.exists(dir / "arc")) os.walk(dir / "arc").filter(os.isFile(_))
              else Nil
            assert(extracted.forall(_.last == ".lock"))
          }
      }

      test("size") {
        withProp("coursier.archive.max-size", "1M") {
          checkRejectedEarly(
            integrityChecked(
              multiFileArchives(Seq("a.txt" -> Array[Byte](1, 2), "big" -> zeros)) ++
                singleFileArchives(zeros)
            ),
            "maximum extracted size (1048576). Set COURSIER_ARCHIVE_MAX_SIZE"
          )
        }
      }
      // entries of tar.gz archives aren't counted there, only their extraction counts them
      test("entries") {
        withProp("coursier.archive.max-entries", "10") {
          checkRejectedEarly(
            multiFileArchives((1 to 20).map(n => s"file-$n" -> Array[Byte](n.toByte)))
              .filter(_._1 == "zip"),
            "maximum number of entries (10). Set COURSIER_ARCHIVE_MAX_ENTRIES"
          )
        }
      }
    }

    test("default limits") {
      assert(UnArchiver.defaultMaxSize == UnArchiver.defaultMaxSizeFallback)
      assert(UnArchiver.defaultMaxEntries == UnArchiver.defaultMaxEntriesFallback)
      withProp("coursier.archive.max-size", "12345") {
        assert(UnArchiver.defaultMaxSize == 12345L)
      }
      withProp("coursier.archive.max-size", "3g") {
        assert(UnArchiver.defaultMaxSize == 3L * 1024L * 1024L * 1024L)
      }
      withProp("coursier.archive.max-size", "0") {
        assert(UnArchiver.defaultMaxSize == 0L)
      }
      withProp("coursier.archive.max-entries", "42") {
        assert(UnArchiver.defaultMaxEntries == 42L)
      }
      withProp("coursier.archive.max-size", "nope") {
        Try(UnArchiver.defaultMaxSize) match {
          case Failure(ex: IllegalArgumentException) =>
            assert(ex.getMessage.contains("coursier.archive.max-size"))
          case other =>
            throw new Exception(s"Expected an IllegalArgumentException, got $other")
        }
      }
      withProp("coursier.archive.max-size", "1024") {
        withTmpDir { dir =>
          val archiveCache = ArchiveCache.create[Task]((dir / "arc").toIO)
            .copy(cache = FileCache[Task]((dir / "cache").toIO))
          val archive = dir / "archives" / "archive.gz"
          os.write(archive, gzip(zeros), createFolders = true)
          val res = Try {
            archiveCache.get(Artifact(archive.toNIO.toUri.toASCIIString))
              .unsafeRun()(archiveCache.cache.ec)
          }
          res match {
            case Failure(ex: UnArchiver.ExtractionLimitException) =>
              assert(ex.getMessage.contains("COURSIER_ARCHIVE_MAX_SIZE"))
            case other =>
              throw new Exception(s"Expected extraction to fail, got $other")
          }
        }
      }
    }

    test("priviledged") {
      test("size") {
        if (!Properties.isWin)
          checkRejected(
            tarArchives(Seq("a.txt" -> Array[Byte](1, 2), "big" -> zeros)),
            maxSize = 1024 * 1024,
            maxEntries = 0,
            expectedMessage = "maximum extracted size",
            unArchiver = priviledged
          )
      }
      test("spread over several entries") {
        if (!Properties.isWin)
          checkRejected(
            tarArchives((1 to 8).map(n => s"file-$n" -> new Array[Byte](bigSize / 8))),
            maxSize = 1024 * 1024,
            maxEntries = 0,
            expectedMessage = "maximum extracted size",
            unArchiver = priviledged
          )
      }
      test("entries") {
        if (!Properties.isWin)
          checkRejected(
            tarArchives((1 to 20).map(n => s"file-$n" -> Array[Byte](n.toByte))),
            maxSize = 0,
            maxEntries = 10,
            expectedMessage = "maximum number of entries",
            unArchiver = priviledged
          )
      }
      test("within limits") {
        val entries = Seq("a.txt" -> Array[Byte](1, 2), "big" -> zeros)
        if (!Properties.isWin)
          for ((ext, content) <- tarArchives(entries))
            withTmpDir { dir =>
              // the size limit applies to the tar stream, headers and padding included
              val res =
                get(dir, ext, content, maxSize = bigSize + 64 * 1024, maxEntries = 2, priviledged)
                  .get
              res match {
                case Left(err) => throw new Exception(s"Extracting $ext archive", err)
                case Right(extracted) =>
                  assert(os.read.bytes(extracted / "a.txt").toSeq == Seq[Byte](1, 2))
                  assert(os.size(extracted / "big") == bigSize.toLong)
              }
            }
      }
      test("invalid archive") {
        // not a tar archive: commons-compress fails, and so does tar, as it gets a truncated stream
        val content = gzip(Array.tabulate[Byte](64 * 1024)(n => (n * 31 + 7).toByte))
        if (!Properties.isWin)
          withTmpDir { dir =>
            val res = get(dir, "tar.gz", content, maxSize = 0, maxEntries = 0, priviledged)
            assert(res.isFailure)
            val leftOver = os.walk(dir / "arc").filter(os.isFile(_)).filter(_.last != ".lock")
            assert(leftOver.isEmpty)
          }
      }
      // tar exiting before having read all of its input, like bsdtar does at the end-of-archive
      // marker, while the JVM still writes the rest of the tar stream to it
      // The archive is way bigger than a pipe buffer, so that writing to tar fails for sure.
      def tarExitingEarly(exitCode: Int): Try[Either[ArtifactError, os.Path]] =
        withTmpDir { dir =>
          val unArchiver = UnArchiver.priviledgedTestMode(
            maxSize = 0L,
            maxEntries = 0L,
            tarCommand = Seq("sh", "-c", s"exit $exitCode")
          )
          get0(dir, "tar", tar(Seq("big" -> zeros)), unArchiver, cacheArchive = false)
        }
      test("tar exiting early successfully") {
        if (!Properties.isWin)
          tarExitingEarly(0) match {
            case Success(Right(_)) =>
            case other =>
              throw new Exception(s"Expected extraction to succeed, got $other")
          }
      }
      test("tar exiting early with an error") {
        if (!Properties.isWin)
          tarExitingEarly(3) match {
            case Failure(ex) =>
              assert(ex.getMessage.contains("see tar command messages above"))
            case other =>
              throw new Exception(s"Expected extraction to fail, got $other")
          }
      }
      test("default limits") {
        if (!Properties.isWin)
          withProp("coursier.archive.max-size", "1024") {
            withTmpDir { dir =>
              val archiveCache = ArchiveCache.create[Task]((dir / "arc").toIO)
                .copy(
                  cache = FileCache[Task]((dir / "cache").toIO),
                  unArchiver = UnArchiver.priviledgedTestMode()
                )
              val archive = dir / "archives" / "archive.tar.gz"
              os.write(archive, gzip(tar(Seq("big" -> zeros))), createFolders = true)
              val res = Try {
                archiveCache.get(Artifact(archive.toNIO.toUri.toASCIIString))
                  .unsafeRun()(archiveCache.cache.ec)
              }
              res match {
                case Failure(ex: UnArchiver.ExtractionLimitException) =>
                  assert(ex.getMessage.contains("COURSIER_ARCHIVE_MAX_SIZE"))
                case other =>
                  throw new Exception(s"Expected extraction to fail, got $other")
              }
            }
          }
      }
    }
  }
}
