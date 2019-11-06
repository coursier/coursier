package coursier.cache.internal

import java.io.{ByteArrayOutputStream, InputStream}

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

  def withContent(is: InputStream, f: WithContent, bufferSize: Int = 16384): Unit = {
    val data = Array.ofDim[Byte](bufferSize)

    var nRead = is.read(data, 0, data.length)
    while (nRead != -1) {
      f(data, nRead)
      nRead = is.read(data, 0, data.length)
    }
  }

  trait WithContent {
    def apply(arr: Array[Byte], z: Int): Unit
  }

  class UpdateDigest(md: java.security.MessageDigest) extends FileUtil.WithContent {
    def apply(arr: Array[Byte], z: Int): Unit = md.update(arr, 0, z)
  }

}
