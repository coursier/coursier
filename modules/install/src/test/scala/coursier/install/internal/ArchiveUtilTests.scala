package coursier.install.internal

import coursier.cache.{ArchiveType, UnArchiver}
import org.apache.commons.compress.archivers.ArchiveOutputStream
import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveOutputStream}
import org.apache.commons.compress.archivers.zip.{ZipArchiveEntry, ZipArchiveOutputStream}
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import utest._

import java.io.{ByteArrayOutputStream, File, InputStream, OutputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Comparator
import java.util.zip.GZIPOutputStream

import scala.util.Using

object ArchiveUtilTests extends TestSuite {

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
  private val xz    = compressed(new XZCompressorOutputStream(_)) _

  // entries with no content are directories
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

  private def withTempDir[T](f: Path => T): T = {
    val dir = Files.createTempDirectory("coursier-archive-util-tests")
    try f(dir)
    finally Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(Files.delete(_))
  }

  private def readAll(is: InputStream): Long = {
    val buf   = new Array[Byte](16 * 1024)
    var total = 0L
    var read  = -1
    while ({ read = is.read(buf); read >= 0 })
      total += read
    total
  }

  private val entries = Seq(
    "dir/"         -> Array.emptyByteArray,
    "dir/launcher" -> zeros,
    "other"        -> Array[Byte](1, 2, 3)
  )

  // archive extension, content, and the size of the launcher read from it, given a size limit
  private def archives: Seq[(String, Array[Byte], (File, Long) => Long)] = Seq(
    (
      "gz",
      gzip(zeros),
      (f, maxSize) => ArchiveUtil.withGzipContent(f, maxSize)(readAll)
    ),
    (
      "xz",
      xz(zeros),
      (f, maxSize) => ArchiveUtil.withCompressedContent(f, ArchiveType.Xz, maxSize)(readAll)
    ),
    (
      "tar.gz",
      gzip(tar(entries)),
      (f, maxSize) =>
        ArchiveUtil.withFirstFileInCompressedTarArchive(f, ArchiveType.Tgz, maxSize)(readAll)
    ),
    (
      "tar.bz2",
      bzip2(tar(entries)),
      (f, maxSize) =>
        ArchiveUtil.withFirstFileInCompressedTarArchive(f, ArchiveType.Tbz2, maxSize)(readAll)
    ),
    (
      "tar",
      tar(entries),
      (f, maxSize) =>
        ArchiveUtil.withFileInCompressedTarArchive(f, ArchiveType.Tar, "dir/launcher", maxSize)(
          readAll
        )
    ),
    (
      "zip",
      zip(entries),
      (f, maxSize) => ArchiveUtil.withFirstFileInZip(f, maxSize)(readAll)
    ),
    (
      "zip (sub-path)",
      zip(entries),
      (f, maxSize) => ArchiveUtil.withFileInZip(f, "dir/launcher", maxSize)(readAll)
    )
  )

  val tests = Tests {

    test("compressed content") {
      val content = "#!/bin/sh\necho hello\n"
      withTempDir { dir =>
        for (
          (archiveType, compress) <- Seq[(ArchiveType.Compressed, Array[Byte] => Array[Byte])](
            ArchiveType.Gzip -> gzip,
            ArchiveType.Xz   -> xz
          )
        ) {
          val f = Files.write(
            dir.resolve(s"launcher.$archiveType"),
            compress(content.getBytes(StandardCharsets.UTF_8))
          ).toFile
          val read = ArchiveUtil.withCompressedContent(f, archiveType) { is =>
            new String(is.readAllBytes(), StandardCharsets.UTF_8)
          }
          assert(read == content)
        }
      }
    }

    test("size limit") {
      withTempDir { dir =>
        for ((name, content, read) <- archives) {
          val f = Files.write(dir.resolve(name.takeWhile(_ != ' ')), content).toFile
          val ex = assertThrows[UnArchiver.ExtractionLimitException] {
            read(f, 1024L * 1024L)
          }
          assert(ex.getMessage.contains("coursier.archive.max-size"))
          assert(ex.getMessage.contains(f.toString))
        }
      }
    }

    test("within limit") {
      withTempDir { dir =>
        for ((name, content, read) <- archives) {
          val f    = Files.write(dir.resolve(name.takeWhile(_ != ' ')), content).toFile
          val size = read(f, bigSize.toLong)
          assert(size == bigSize.toLong)
          // 0 disables the limit
          val size0 = read(f, 0L)
          assert(size0 == bigSize.toLong)
        }
      }
    }

    test("lots of entries before the launcher") {
      // skipped, not extracted, so they don't count towards the size limit
      val manyEntries = (1 to 2000).map(n => s"dir$n/" -> Array.emptyByteArray) ++
        Seq("launcher" -> Array[Byte](1, 2, 3))
      withTempDir { dir =>
        val f = Files.write(dir.resolve("many.tar.gz"), gzip(tar(manyEntries))).toFile
        val size =
          ArchiveUtil.withFirstFileInCompressedTarArchive(f, ArchiveType.Tgz, 3L)(readAll)
        assert(size == 3L)
      }
    }
  }

}
