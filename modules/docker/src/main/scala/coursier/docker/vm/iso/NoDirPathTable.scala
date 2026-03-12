package coursier.docker.vm.iso

import coursier.docker.vm.iso.Structs._

import java.nio.ByteBuffer
import java.io.OutputStream
import java.io.ByteArrayOutputStream

final case class NoDirPathTable(rootDir: Directory, isLittleEndian: Boolean = true) extends Record {
  def pathTableLength: Int = {
    val baos = new ByteArrayOutputStream
    writeTo(baos, Indices.zeros)
    baos.toByteArray.length
  }
  def littleEndian: NoDirPathTable =
    if (isLittleEndian) this else copy(isLittleEndian = true)
  def bigEndian: NoDirPathTable =
    if (isLittleEndian) copy(isLittleEndian = false) else this

  private def writeTo(out: OutputStream, indices: Indices): Unit = {

    val rootDirRecord = PathTableRecord.empty.copy(
      lenDi = 1,
      parentDirNum = 1,
      locationOfExtent = indices(rootDir)
    )

    val codec =
      if (isLittleEndian) pathTableRecordCodecL else pathTableRecordCodec
    val bytes = codec.encode(rootDirRecord).require.toByteArray
    out.write(bytes)
  }

  def doWrite(buf: ByteBuffer, indices: Indices): Unit = {
    val baos = new ByteArrayOutputStream
    writeTo(baos, Indices.zeros)
    val bytes = baos.toByteArray
    buf.put(bytes)
    assert(bytes.length <= 2048)
    buf.put(Record.empty, 0, 2048 - bytes.length)
  }
}
