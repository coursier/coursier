package coursier.docker.vm.iso

import coursier.docker.vm.iso.Structs.{
  Directory => DirectoryFlag,
  DirectoryRecord,
  directoryRecordCodec
}

import java.nio.ByteBuffer

final class DotDirectoryEntry(directory: Directory) extends DirectoryEntry {
  def write(buf: ByteBuffer, indices: Indices, dummy: Boolean): Unit = {
    val rec = DirectoryRecord.empty.copy(
      lenDr = 34,
      location = indices(directory),
      dataLen = if (dummy) 0 else directory.lengthInBytes,
      fileFlags = DirectoryFlag,
      volumeSequenceNumber = 1,
      lenFi = 1 // 1 ???
    )
    val b        = directoryRecordCodec.encode(rec).require.bytes.toArray
    val startPos = buf.position()
    buf.put(b)
    buf.put(0: Byte)
    val endPos = buf.position()
    assert(endPos - startPos == 34)
  }
}
