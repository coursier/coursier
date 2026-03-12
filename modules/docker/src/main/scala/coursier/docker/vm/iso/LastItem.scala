package coursier.docker.vm.iso

import java.nio.ByteBuffer

case object LastItem extends Records {
  def lengthInSectors: Int                             = 0
  def doWrite(buf: ByteBuffer, indices: Indices): Unit = ()
}
