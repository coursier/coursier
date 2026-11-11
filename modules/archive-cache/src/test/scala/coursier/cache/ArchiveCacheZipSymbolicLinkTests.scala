package coursier.cache

import coursier.cache.TestUtil._
import coursier.util.{Artifact, Task}
import org.apache.commons.compress.archivers.zip.{ZipArchiveEntry, ZipArchiveOutputStream}
import org.codehaus.plexus.archiver.ArchiverException
import utest._

import java.nio.charset.StandardCharsets
import java.nio.{ByteBuffer, ByteOrder}

import scala.util.{Failure, Properties, Try, Using}

object ArchiveCacheZipSymbolicLinkTests extends TestSuite {

  private def symlinkMode = 0x8000 | 0x2000 | 0x1ff // S_IFLNK | 0777

  // entries: name -> Left(file content) or Right(symbolic link target)
  private def writeZip(dest: os.Path, entries: Seq[(String, Either[String, Array[Byte]])]): Unit = {
    os.makeDir.all(dest / os.up)
    Using.resource(new ZipArchiveOutputStream(dest.toIO)) { zos =>
      for ((name, content) <- entries) {
        val entry = new ZipArchiveEntry(name)
        val bytes = content match {
          case Left(text) => text.getBytes(StandardCharsets.UTF_8)
          case Right(target) =>
            entry.setUnixMode(symlinkMode)
            target
        }
        zos.putArchiveEntry(entry)
        zos.write(bytes)
        zos.closeArchiveEntry()
      }
      zos.finish()
    }
  }

  // Changes the uncompressed size of all entries in the central directory of a zip archive,
  // where commons-compress' ZipFile reads it from
  private def fakeUncompressedSize(zip: os.Path, size: Int): Unit = {
    val bytes = os.read.bytes(zip)
    val buf   = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    var found = 0
    for (i <- 0 until bytes.length - 4 if buf.getInt(i) == 0x02014b50) {
      buf.putInt(i + 24, size)
      found += 1
    }
    assert(found > 0)
    os.write.over(zip, bytes)
  }

  private def get(dir: os.Path, zip: os.Path): Either[ArtifactError, os.Path] = {
    val archiveCache = ArchiveCache.create[Task]((dir / "arc").toIO)
      .copy(cache = FileCache[Task]((dir / "cache").toIO))
    archiveCache.get(Artifact(zip.toNIO.toUri.toASCIIString))
      .unsafeRun()(archiveCache.cache.ec)
      .map(os.Path(_))
  }

  // highly compressible, 64 KB or so once compressed
  private def hugeTarget = Array.fill[Byte](64 * 1024 * 1024)('a'.toByte)

  private def checkRejected(dir: os.Path, zip: os.Path): Unit = {
    Try(get(dir, zip)) match {
      case Failure(ex: ArchiverException) =>
        // rejected by our check, before plexus-archiver reads the link target in memory
        assert(ex.getMessage.contains("has a target longer than 4096 bytes"))
      case other =>
        throw new Exception(s"Expected extraction to fail, got $other")
    }
    val leftOver = os.walk(dir / "arc").filter(os.isFile(_)).filter(_.last != ".lock")
    assert(leftOver.isEmpty)
  }

  val tests =
    if (Properties.isWin) Tests {} // creating symbolic links requires specific rights there
    else
      Tests {

        test("short link accepted") {
          withTmpDir { dir =>
            val zip = dir / "archives" / "archive.zip"
            writeZip(
              zip,
              Seq(
                "a.txt" -> Left("a"),
                "link"  -> Right("a.txt".getBytes(StandardCharsets.UTF_8))
              )
            )
            val extracted = get(dir, zip).fold(e => throw new Exception(e), identity)
            assert(os.isLink(extracted / "link"))
            assert(os.read(extracted / "link") == "a")
          }
        }

        test("huge link rejected") {
          withTmpDir { dir =>
            val zip = dir / "archives" / "archive.zip"
            writeZip(zip, Seq("link" -> Right(hugeTarget)))
            checkRejected(dir, zip)
          }
        }

        // the size of the entry in the archive is small, but its content isn't
        test("huge link with fake size rejected") {
          withTmpDir { dir =>
            val zip = dir / "archives" / "archive.zip"
            writeZip(zip, Seq("link" -> Right(hugeTarget)))
            fakeUncompressedSize(zip, 10)
            checkRejected(dir, zip)
          }
        }
      }
}
