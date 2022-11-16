package coursier.launcher.internal

import java.util.zip.{ZipEntry, ZipFile}

import scala.jdk.CollectionConverters._

private[coursier] object Zip {

  def zipEntries(zipFile: ZipFile): Iterator[(ZipEntry, Array[Byte])] =
    zipFile.entries().asScala.map { ent =>
      val data = FileUtil.readFully(zipFile.getInputStream(ent))

      // Doing this like above just in case
      ent.setCompressedSize(-1L)

      (ent, data)
    }

}
