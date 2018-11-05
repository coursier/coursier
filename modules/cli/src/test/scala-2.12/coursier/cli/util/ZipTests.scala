package coursier.cli.util

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.Random
import java.util.zip.{Deflater, ZipEntry, ZipInputStream, ZipOutputStream}

import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ZipTests extends FlatSpec {

  "zipEntries" should "be fine with custom deflaters" in {

    // Inspired by https://github.com/spring-projects/spring-boot/commit/a50646b7cc3ad941e748dfb450077e3a73706205#diff-2297c301250b25e3b80301c58daf3ea0R621

    val baos = new ByteArrayOutputStream
    val output = new ZipOutputStream(baos) {
      `def` = new Deflater(Deflater.NO_COMPRESSION, true)
    }
    val data = Array.ofDim[Byte](1024 * 1024)
    new Random().nextBytes(data)
    val entry = new ZipEntry("entry.dat")
    output.putNextEntry(entry)
    output.write(data)
    output.closeEntry()
    output.close()

    val result = baos.toByteArray

    val zos = new ZipOutputStream(new ByteArrayOutputStream)
    val entryNames = Zip.zipEntries(new ZipInputStream(new ByteArrayInputStream(result)))
      .map {
        case (ent, content) =>
          println(ent.getCompressedSize)
          val name = ent.getName
          zos.putNextEntry(ent)
          zos.write(content)
          zos.closeEntry()
          name
      }
      .toVector
    zos.close()
    assert(entryNames == Vector("entry.dat"))
  }

}
