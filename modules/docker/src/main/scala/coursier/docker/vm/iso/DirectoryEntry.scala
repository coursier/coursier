package coursier.docker.vm.iso

import java.nio.ByteBuffer

abstract class DirectoryEntry {
  def write(buf: ByteBuffer, indices: Indices, dummy: Boolean): Unit
}
