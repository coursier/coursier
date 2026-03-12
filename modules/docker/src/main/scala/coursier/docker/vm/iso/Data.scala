package coursier.docker.vm.iso

import java.nio.ByteBuffer

final class Data(data: Array[Byte]) extends Records {
  def lengthInBytes: Int = data.length
  def lengthInSectors: Int =
    data.length / 2048 + (if (data.length % 2048 == 0) 0 else 1)
  def doWrite(buf: ByteBuffer, indices: Indices): Unit = {
    buf.put(data)
    val paddingLen = lengthInSectors * 2048 - data.length
    assert(paddingLen >= 0)
    assert(paddingLen < 2048)
    buf.put(Record.empty, 0, paddingLen)
  }
}
