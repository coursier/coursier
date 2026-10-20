package coursier.cache.internal

import java.io.{ByteArrayOutputStream, InputStream, OutputStream}
import java.nio.file.{Files, LinkOption, Path, StandardOpenOption}

import coursier.paths.Util

object FileUtil {

  /** Opens `path` for writing, replacing a symbolic link there rather than following it
    *
    * So that a link planted in the cache by someone else having write access to it can't make us
    * write to another file.
    */
  def newOutputStreamNoFollow(path: Path, append: Boolean = false): OutputStream = {
    Util.deleteIfSymbolicLink(path)
    Files.newOutputStream(
      path,
      StandardOpenOption.CREATE,
      StandardOpenOption.WRITE,
      if (append) StandardOpenOption.APPEND else StandardOpenOption.TRUNCATE_EXISTING,
      LinkOption.NOFOLLOW_LINKS
    )
  }

  /** Writes `content` to `path`, replacing a symbolic link there rather than following it */
  def writeNoFollow(path: Path, content: Array[Byte]): Unit = {
    val os = newOutputStreamNoFollow(path)
    try os.write(content)
    finally os.close()
  }

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
