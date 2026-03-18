package coursier.docker.vm.iso

import coursier.docker.vm.iso.Structs._

import java.nio.ByteBuffer

case object VolumeDescriptorTerminator extends Record {

  def doWrite(buf: ByteBuffer, indices: Indices): Unit = {
    val header = Header(
      `type` = PrimaryType,
      stdId = ZeroPaddedByteArray.create(cd001),
      version = Structs.Version
    )

    val bytes = headerCodec.encode(header).require.toByteArray
    assert(bytes.length == 7)
    buf.put(bytes)
    buf.put(Record.empty, 0, 2048 - bytes.length)
  }
}
