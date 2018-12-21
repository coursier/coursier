package coursier.bootstrap.util

import java.io.{ByteArrayOutputStream, InputStream}
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.{Files, Path}

import scala.collection.JavaConverters._

object FileUtil {

  // Won't be necessary anymore with Java 9
  // (https://docs.oracle.com/javase/9/docs/api/java/io/InputStream.html#readAllBytes--,
  // via https://stackoverflow.com/questions/1264709/convert-inputstream-to-byte-array-in-java/37681322#37681322)
  def readFullyUnsafe(is: InputStream): Array[Byte] = {
    val buffer = new ByteArrayOutputStream
    val data = Array.ofDim[Byte](16384)

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
    } finally {
      if (is0 != null)
        is0.close()
    }
  }

  def tryMakeExecutable(path: Path): Boolean = {

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
    } catch {
      case _: UnsupportedOperationException =>
        false
    }
  }

}
