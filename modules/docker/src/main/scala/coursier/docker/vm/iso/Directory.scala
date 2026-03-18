package coursier.docker.vm.iso

import java.nio.ByteBuffer
import java.io.InputStream
import java.io.OutputStream
import java.io.ByteArrayOutputStream

final class Directory(
  private var getEntries: () => Seq[DirectoryEntry]
) extends Records {
  private lazy val entries = {
    val res = getEntries()
    getEntries = null
    res
  }
  lazy val lengthInBytes: Int = {
    val baos = new ByteArrayOutputStream
    writeTo(baos, Indices.zeros, dummy = true)
    baos.toByteArray.length
  }
  override def lengthInSectors: Int =
    lengthInBytes / 2048 + (if (lengthInBytes % 2048 == 0) 0 else 1)

  private def writeTo(out: OutputStream, indices: Indices, dummy: Boolean): Unit = {
    val tmpBytes    = Array.fill(2048)(0: Byte)
    val tmpBuf      = ByteBuffer.wrap(tmpBytes)
    var sectorCount = 0
    var byteCount   = 0

    for (ent <- entries) {
      tmpBuf.position(0)
      ent.write(tmpBuf, indices, dummy)
      val entryLen = tmpBuf.position()
      assert(entryLen > 0)

      if (byteCount + entryLen > 2048) {
        out.write(Record.empty, 0, 2048 - byteCount)
        sectorCount += 1
        byteCount = 0
      }
      assert(byteCount + entryLen <= 2048)

      out.write(tmpBytes, 0, entryLen)
      byteCount += entryLen

      if (byteCount == 2048) {
        sectorCount += 1
        byteCount = 0
      }
    }
  }

  override def doWrite(buf: ByteBuffer, indices: Indices): Unit = {
    val baos = new ByteArrayOutputStream
    writeTo(baos, indices, dummy = false)
    val b = baos.toByteArray
    buf.put(b)
    val padding = lengthInSectors * 2048 - lengthInBytes
    assert(padding >= 0)
    assert(padding < 2048)
    buf.put(Record.empty, 0, padding)
  }
}
