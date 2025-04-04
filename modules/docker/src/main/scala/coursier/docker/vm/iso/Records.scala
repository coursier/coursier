package coursier.docker.vm.iso

import java.nio.ByteBuffer

abstract class Records {
  def lengthInSectors: Int
  def doWrite(buf: ByteBuffer, indices: Indices): Unit

  def write(buf: ByteBuffer, indices: Indices): Unit = {
    val startPos = buf.position()
    doWrite(buf, indices)
    val endPos = buf.position()
    assert((endPos - startPos) % 2048 == 0)
  }
}
