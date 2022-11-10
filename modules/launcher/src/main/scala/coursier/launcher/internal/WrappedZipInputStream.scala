package coursier.launcher.internal

import java.io.{ByteArrayOutputStream, Closeable, InputStream}
import java.util.zip.ZipEntry

/*
 * juz.ZipInputStream is buggy on Arch Linux from native images (CRC32 calculation issues,
 * see oracle/graalvm#4479), so we use a custom ZipInputStream with disabled CRC32 calculation.
 */
final case class WrappedZipInputStream(
  wrapped: Either[io.github.scala_cli.zip.ZipInputStream, java.util.zip.ZipInputStream]
) extends Closeable {
  def entries(): Iterator[ZipEntry] = {
    val getNextEntry = wrapped match {
      case Left(zis)  => () => zis.getNextEntry()
      case Right(zis) => () => zis.getNextEntry()
    }
    Iterator.continually(getNextEntry()).takeWhile(_ != null)
  }
  def entriesWithData(): Iterator[(ZipEntry, Array[Byte])] = {
    val getNextEntry = wrapped match {
      case Left(zis)  => () => zis.getNextEntry()
      case Right(zis) => () => zis.getNextEntry()
    }
    val getNextEntryAndData = () => {
      val ent  = getNextEntry()
      val data = readAllBytes()

      // ZipInputStream seems not to be fine with custom deflaters, like some recent versions of proguard use.
      // This makes ZipOutputStream not handle some of these entries fine without this.
      // See https://github.com/spring-projects/spring-boot/issues/13720#issuecomment-403428384.
      // Same workaround as https://github.com/spring-projects/spring-boot/issues/13720
      // (https://github.com/spring-projects/spring-boot/commit/a50646b7cc3ad941e748dfb450077e3a73706205#diff-2ff64cd06c0b25857e3e0dfdb6733174R144)
      ent.setCompressedSize(-1L)

      (ent, data)
    }
    Iterator.continually(getNextEntryAndData()).takeWhile(_ != null)
  }
  def closeEntry(): Unit =
    wrapped match {
      case Left(zis)  => zis.closeEntry()
      case Right(zis) => zis.closeEntry()
    }
  def readAllBytes(): Array[Byte] = {
    val baos = new ByteArrayOutputStream
    val is   = wrapped.merge
    val buf  = Array.ofDim[Byte](16 * 1024)
    var read = 0
    while ({
      read = is.read(buf)
      read >= 0
    })
      if (read > 0)
        baos.write(buf, 0, read)
    baos.toByteArray
  }
  def close(): Unit =
    wrapped.merge.close()
}

object WrappedZipInputStream {
  lazy val shouldUseVendoredImplem = {
    def toBoolean(input: String): Boolean =
      input match {
        case "true" | "1" => true
        case _            => false
      }
    Option(System.getenv("COURSIER_VENDORED_ZIS")).map(toBoolean)
      .orElse(sys.props.get("coursier.zis.vendored").map(toBoolean))
      // accepting the same variable names as Scala CLI too
      .orElse(Option(System.getenv("SCALA_CLI_VENDORED_ZIS")).map(toBoolean))
      .orElse(sys.props.get("scala-cli.zis.vendored").map(toBoolean))
      .getOrElse(false)
  }
  def create(is: InputStream): WrappedZipInputStream =
    WrappedZipInputStream {
      if (shouldUseVendoredImplem) Left(new io.github.scala_cli.zip.ZipInputStream(is))
      else Right(new java.util.zip.ZipInputStream(is))
    }
}
