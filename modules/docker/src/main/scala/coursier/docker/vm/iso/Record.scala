package coursier.docker.vm.iso

import java.nio.ByteBuffer

abstract class Record extends Records {
  final def lengthInSectors: Int = 1
  def doWrite(buf: ByteBuffer, indices: Indices): Unit

  override def write(buf: ByteBuffer, indices: Indices): Unit = {
    val startPos = buf.position()
    doWrite(buf, indices)
    val endPos = buf.position()
    assert(
      endPos - startPos == 2048,
      s"startPos=$startPos, endPos=$endPos, expected endPos=${startPos + 2048} in $getClass"
    )
  }
}

object Record {
  val empty = Array.fill(2048)(0: Byte)
}
