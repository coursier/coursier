package coursier.cache

import coursier.cache.TestUtil._
import coursier.util.{Artifact, Task}
import org.apache.commons.compress.archivers.ArchiveOutputStream
import org.apache.commons.compress.archivers.ar.{ArArchiveEntry, ArArchiveOutputStream}
import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveOutputStream}
import org.apache.commons.compress.archivers.zip.{ZipArchiveEntry, ZipArchiveOutputStream}
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import utest._

import java.io.{ByteArrayOutputStream, OutputStream}
import java.util.zip.GZIPOutputStream

import scala.util.{Failure, Try, Using}

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
  // no xz archives here, as org.tukaani:xz isn't on the class path
  private def multiFileArchives(entries: Seq[(String, Array[Byte])]): Seq[(String, Array[Byte])] =
    Seq(
      "tar"     -> tar(entries),
      "tar.gz"  -> gzip(tar(entries)),
      "tar.bz2" -> bzip2(tar(entries)),
      "tar.zst" -> zstd(tar(entries)),
      "zip"     -> zip(entries),
      "ar"      -> ar(entries)
    )
  private def singleFileArchives(content: Array[Byte]): Seq[(String, Array[Byte])] =
    Seq(
      "gz" -> gzip(content)
    )

  private def get(
    dir: os.Path,
    ext: String,
    content: Array[Byte],
    maxSize: Long,
    maxEntries: Long
  ): Try[Either[ArtifactError, os.Path]] = {
    val unArchiver = UnArchiver.default(maxSize, maxEntries)
    val archiveCache = ArchiveCache.create[Task]((dir / "arc").toIO)
      .copy(cache = FileCache[Task]((dir / "cache").toIO), unArchiver = unArchiver)
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
    expectedMessage: String
  ): Unit =
    for ((ext, content) <- archives)
      withTmpDir { dir =>
        get(dir, ext, content, maxSize, maxEntries) match {
          case Failure(ex: UnArchiver.ExtractionLimitException) =>
            assert(ex.getMessage.contains(expectedMessage))
          case other =>
            throw new Exception(s"Expected extraction of $ext archive to fail, got $other")
        }
        // the partial extraction got removed
        val leftOver = os.walk(dir / "arc").filter(os.isFile(_)).filter(_.last != ".lock")
        assert(leftOver.isEmpty)
      }

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

    test("within limits") {
      val entries = Seq("a.txt" -> Array[Byte](1, 2), "big" -> zeros)
      for ((ext, content) <- multiFileArchives(entries))
        withTmpDir { dir =>
          val res = get(dir, ext, content, maxSize = bigSize + 2, maxEntries = 2).get
          res match {
            case Left(err) => throw new Exception(s"Extracting $ext archive", err)
            case Right(extracted) =>
              assert(os.size(extracted / "big") == bigSize.toLong)
          }
        }
      for ((ext, content) <- singleFileArchives(zeros))
        withTmpDir { dir =>
          val res = get(dir, ext, content, maxSize = bigSize, maxEntries = 1).get
          res match {
            case Left(err)        => throw new Exception(s"Extracting $ext archive", err)
            case Right(extracted) => assert(os.size(extracted) == bigSize.toLong)
          }
        }
    }

    test("default limits") {
      def withProp[T](name: String, value: String)(f: => T): T = {
        val former = sys.props.get(name)
        sys.props(name) = value
        try f
        finally former match {
            case Some(v) => sys.props(name) = v
            case None    => sys.props.remove(name)
          }
      }
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
  }
}
