package coursier.docker.vm.iso

import java.nio.ByteBuffer

final class EmptyRecords(val lengthInSectors: Int) extends Records {
  def doWrite(buf: ByteBuffer, indices: Indices): Unit =
    for (_ <- 1 to lengthInSectors)
      buf.put(Record.empty)
}
