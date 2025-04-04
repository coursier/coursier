package coursier.docker.vm.iso

import coursier.docker.vm.iso.Structs.{DirectoryRecord, directoryRecordCodec}

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

final class FileDirectoryEntry(fileName: String, data: Data) extends DirectoryEntry {
  private lazy val fileNameBytes = fileName.getBytes(StandardCharsets.US_ASCII)
  override def write(buf: ByteBuffer, indices: Indices, dummy: Boolean): Unit = {
    val rec = DirectoryRecord.empty.copy(
      lenDr = 33 + fileNameBytes.length + (if (fileNameBytes.length % 2 == 0) 1 else 0),
      location = indices(data),
      dataLen = if (dummy) 0 else data.lengthInBytes,
      volumeSequenceNumber = 1,
      lenFi = fileNameBytes.length
    )
    val b = directoryRecordCodec.encode(rec).require.bytes.toArray
    buf.put(b)
    // Ideally, we should only put DOS file names here (8.3 format), and
    // use the Joliet / RockRidge ISO9660 extensions for the full names.
    // It seems the Linux kernel is fine with fancy names here, so that
    // works for us.
    buf.put(fileNameBytes)
    if (fileNameBytes.length % 2 == 0)
      buf.put(0: Byte)
  }
}
