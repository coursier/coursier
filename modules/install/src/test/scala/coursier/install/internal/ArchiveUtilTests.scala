package coursier.install.internal

import coursier.cache.ArchiveType
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream
import utest._

import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

object ArchiveUtilTests extends TestSuite {

  private val content = "#!/bin/sh\necho hello\n"

  private def check(archiveType: ArchiveType.Compressed)(compress: OutputStream => OutputStream)
    : Unit = {
    val tmpDir = os.temp.dir(prefix = "coursier-archive-util-tests")
    try {
      val file = tmpDir / "launcher.compressed"
      val os0  = compress(Files.newOutputStream(file.toNIO))
      try os0.write(content.getBytes(StandardCharsets.UTF_8))
      finally os0.close()

      val read = ArchiveUtil.withCompressedContent(file.toIO, archiveType) { is =>
        new String(is.readAllBytes(), StandardCharsets.UTF_8)
      }
      assert(read == content)
    }
    finally os.remove.all(tmpDir)
  }

  val tests = Tests {
    test("gzip") {
      check(ArchiveType.Gzip)(new GZIPOutputStream(_))
    }
    test("xz") {
      check(ArchiveType.Xz)(new XZCompressorOutputStream(_))
    }
  }
}
