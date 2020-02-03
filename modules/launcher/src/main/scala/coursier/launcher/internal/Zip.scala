package coursier.launcher.internal

import java.util.zip.{ZipEntry, ZipFile, ZipInputStream}

import scala.collection.JavaConverters._

private[coursier] object Zip {

  def zipEntries(zipStream: ZipInputStream): Iterator[(ZipEntry, Array[Byte])] =
    new Iterator[(ZipEntry, Array[Byte])] {
      var nextEntry = Option.empty[ZipEntry]
      def update() =
        nextEntry = Option(zipStream.getNextEntry)

      update()

      def hasNext = nextEntry.nonEmpty
      def next() = {
        val ent = nextEntry.get
        val data = FileUtil.readFullyUnsafe(zipStream)

        update()

        // ZipInputStream seems not to be fine with custom deflaters, like some recent versions of proguard use.
        // This makes ZipOutputStream not handle some of these entries fine without this.
        // See https://github.com/spring-projects/spring-boot/issues/13720#issuecomment-403428384.
        // Same workaround as https://github.com/spring-projects/spring-boot/issues/13720
        // (https://github.com/spring-projects/spring-boot/commit/a50646b7cc3ad941e748dfb450077e3a73706205#diff-2ff64cd06c0b25857e3e0dfdb6733174R144)
        ent.setCompressedSize(-1L)

        (ent, data)
      }
    }

  def zipEntries(zipFile: ZipFile): Iterator[(ZipEntry, Array[Byte])] =
    zipFile.entries().asScala.map { ent =>
      val data = FileUtil.readFully(zipFile.getInputStream(ent))

      // Doing this like above just in case
      ent.setCompressedSize(-1L)

      (ent, data)
    }

}
