package coursier.launcher.internal

import java.io.{ByteArrayOutputStream, InputStream, OutputStream}
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{Files, LinkOption, Path}

import scala.jdk.CollectionConverters._

private[coursier] object FileUtil {

  // Won't be necessary anymore with Java 9
  // (https://docs.oracle.com/javase/9/docs/api/java/io/InputStream.html#readAllBytes--,
  // via https://stackoverflow.com/questions/1264709/convert-inputstream-to-byte-array-in-java/37681322#37681322)
  def readFullyUnsafe(is: InputStream): Array[Byte] = {
    val buffer = new ByteArrayOutputStream
    val data   = Array.ofDim[Byte](16384)

    var nRead = 0
    while ({
      nRead = is.read(data, 0, data.length)
      nRead != -1
    })
      buffer.write(data, 0, nRead)

    buffer.flush()
    buffer.toByteArray
  }

  def readFully(is: => InputStream): Array[Byte] = {
    var is0: InputStream = null
    try {
      is0 = is
      readFullyUnsafe(is0)
    }
    finally if (is0 != null)
      is0.close()
  }

  def withOutputStream[T](path: Path)(f: OutputStream => T): T = {
    var os: OutputStream = null
    try {
      os = Files.newOutputStream(path)
      f(os)
    }
    finally if (os != null)
      os.close()
  }

  def tryMakeExecutable(path: Path): Boolean =
    try {
      val perms = Files.getPosixFilePermissions(path).asScala.toSet

      var newPerms = perms
      if (perms(PosixFilePermission.OWNER_READ))
        newPerms += PosixFilePermission.OWNER_EXECUTE
      if (perms(PosixFilePermission.GROUP_READ))
        newPerms += PosixFilePermission.GROUP_EXECUTE
      if (perms(PosixFilePermission.OTHERS_READ))
        newPerms += PosixFilePermission.OTHERS_EXECUTE

      if (newPerms != perms)
        Files.setPosixFilePermissions(
          path,
          newPerms.asJava
        )

      true
    }
    catch {
      case _: UnsupportedOperationException =>
        false
    }

  def tryHideWindows(path: Path): Boolean =
    scala.util.Properties.isWin && {
      try {
        Files.setAttribute(path, "dos:hidden", java.lang.Boolean.TRUE, LinkOption.NOFOLLOW_LINKS)
        true
      }
      catch {
        case _: UnsupportedOperationException =>
          false
      }
    }

}
