package coursier.cache

import coursier.cache.TestUtil._
import coursier.util.{Artifact, Task}
import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveOutputStream}
import org.apache.commons.compress.archivers.zip.{ZipArchiveEntry, ZipArchiveOutputStream}
import org.codehaus.plexus.components.io.attributes.AttributeUtils
import utest._

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

import scala.util.{Properties, Using}

object ArchiveCachePermissionsTests extends TestSuite {

  private def octal(s: String): Int = Integer.parseInt(s, 8)

  // file name -> mode
  private val entries = Seq(
    "rwx-all"     -> "777",
    "rw-all"      -> "666",
    "setuid"      -> "4755",
    "setgid"      -> "2775",
    "sticky"      -> "1777",
    "bin/java"    -> "755",
    "regular"     -> "644",
    "owner-only"  -> "700",
    "group-write" -> "664"
  )

  // read independently of coursier.exec.Umask, which the archive cache relies on
  private lazy val umask = octal(os.proc("sh", "-c", "umask").call().out.trim())

  // what extracted files are expected to end up with: no setuid / setgid / sticky bits, and the
  // umask applied
  private def expected: Map[String, String] =
    entries.map {
      case (name, mode) =>
        val perms = octal(mode) & octal("777") & ~umask
        name -> PosixFilePermissions.toString(AttributeUtils.getPermissions(perms))
    }.toMap

  private def content(name: String): Array[Byte] =
    s"content of $name".getBytes(StandardCharsets.UTF_8)

  private def writeTar(dest: os.Path): Unit =
    Using.resource(new TarArchiveOutputStream(os.write.outputStream(dest, createFolders = true))) {
      tos =>
        for ((name, mode) <- entries) {
          val bytes = content(name)
          val entry = new TarArchiveEntry(name)
          entry.setSize(bytes.length)
          entry.setMode(octal(mode))
          tos.putArchiveEntry(entry)
          tos.write(bytes)
          tos.closeArchiveEntry()
        }
    }

  private def writeZip(dest: os.Path): Unit =
    Using.resource(new ZipArchiveOutputStream(os.write.outputStream(dest, createFolders = true))) {
      zos =>
        for ((name, mode) <- entries) {
          val entry = new ZipArchiveEntry(name)
          entry.setUnixMode(octal(mode))
          zos.putArchiveEntry(entry)
          zos.write(content(name))
          zos.closeArchiveEntry()
        }
    }

  private def check(archiveName: String, write: os.Path => Unit): Unit =
    withTmpDir { dir =>
      val archiveCache = ArchiveCache.create[Task]((dir / "arc").toIO)
        .copy(cache = FileCache[Task]((dir / "cache").toIO))
      val archive = dir / "archives" / archiveName
      write(archive)
      val artifact = Artifact(archive.toNIO.toUri.toASCIIString)
      val extracted = archiveCache.get(artifact).unsafeRun()(archiveCache.cache.ec)
        .fold(e => throw new Exception(e), os.Path(_))
      val perms = entries.map {
        case (name, _) =>
          val f = extracted / os.RelPath(name)
          assert(os.read.bytes(f).sameElements(content(name)))
          name -> PosixFilePermissions.toString(Files.getPosixFilePermissions(f.toNIO))
      }.toMap
      assert(perms == expected)
      // with the common 022 umask, nothing is group or world-writable
      if (umask == octal("022"))
        assert(perms.values.forall(p => p(4) != 'w' && p(7) != 'w'))
    }

  val tests =
    if (Properties.isWin) Tests {} // no POSIX permissions there
    else
      Tests {
        test("tar") {
          check("archive.tar", writeTar)
        }
        test("zip") {
          check("archive.zip", writeZip)
        }
      }
}
