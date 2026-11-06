package coursierbuild

import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

object ZipUtil {

  private def zipEntries(zipStream: ZipInputStream): Iterator[(ZipEntry, Array[Byte])] =
    new Iterator[(ZipEntry, Array[Byte])] {
      private var nextEntry = Option.empty[ZipEntry]
      private def update() =
        nextEntry = Option(zipStream.getNextEntry)

      update()

      def hasNext = nextEntry.nonEmpty
      def next() = {
        val ent  = nextEntry.get
        val data = zipStream.readAllBytes()

        update()

        (ent, data)
      }
    }

  def removeFromZip(sourceZip: os.Path, destZip: os.Path, remove: String => Boolean): Unit = {

    val is           = os.read.inputStream(sourceZip)
    val os0          = os.write.outputStream(destZip)
    val bootstrapZip = new ZipInputStream(is)
    val outputZip    = new ZipOutputStream(os0)

    for ((ent, data) <- zipEntries(bootstrapZip) if !remove(ent.getName)) {

      // Same workaround as https://github.com/spring-projects/spring-boot/issues/13720
      // (https://github.com/spring-projects/spring-boot/commit/a50646b7cc3ad941e748dfb450077e3a73706205#diff-2ff64cd06c0b25857e3e0dfdb6733174R144)
      ent.setCompressedSize(-1L)

      outputZip.putNextEntry(ent)
      outputZip.write(data)
      outputZip.closeEntry()
    }

    outputZip.finish()
    outputZip.close()

    is.close()
    os0.close()
  }

}
