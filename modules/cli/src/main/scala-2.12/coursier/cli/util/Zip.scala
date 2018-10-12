package coursier.cli.util

import java.util.zip.{ZipEntry, ZipInputStream}

object Zip {

  def zipEntries(zipStream: ZipInputStream): Iterator[(ZipEntry, Array[Byte])] =
    new Iterator[(ZipEntry, Array[Byte])] {
      var nextEntry = Option.empty[ZipEntry]
      def update() =
        nextEntry = Option(zipStream.getNextEntry)

      update()

      def hasNext = nextEntry.nonEmpty
      def next() = {
        val ent = nextEntry.get
        val data = coursier.internal.FileUtil.readFully(zipStream)

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

}
