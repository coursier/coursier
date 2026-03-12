package coursier.docker.vm.iso

import coursier.docker.vm.iso.Structs._

import java.nio.ByteBuffer

final case class PrimaryVolumeDescriptor(
  rootDirectoryRecord: DotDirectoryEntry,
  pathTable: NoDirPathTable,
  lastItem: Records,
  volumeIdentifier: String
) extends Record {

  def doWrite(buf: ByteBuffer, indices: Indices): Unit = {
    val rootDirBytes = Array.fill(34)(0: Byte)
    val rootDirBuf   = ByteBuffer.wrap(rootDirBytes)
    rootDirectoryRecord.write(rootDirBuf, indices, dummy = false)

    val tvd = TreeVolumeDescriptor.empty.copy(
      header = Header(
        `type` = PrimaryType,
        stdId = ZeroPaddedByteArray.create(cd001),
        version = Structs.Version
      ),
      volumeIdentifier = PaddedString.create(volumeIdentifier.take(32)),
      volumeSpaceSize = indices(lastItem),
      volumeSetSize = 1,
      volumeSequenceNumber = 1,
      logicalBlockSize = 2048,
      pathTableStuff = PathTableStuff.empty.copy(
        pathTableSize = pathTable.pathTableLength,
        pathTableLocation = indices(pathTable.littleEndian),
        mPathTableLocation = indices(pathTable.bigEndian)
      ),
      rootDirectoryRecord = ZeroPaddedByteArray.create(rootDirBytes),
      volumeCreationDate = Some(defaultDate),
      volumeModificationDate = Some(defaultDate),
      volumeExpirationDate = Some(defaultDate),
      volumeEffectiveDate = Some(defaultDate),
      fileStructureVersion = FileStructureVersion
    )

    val bytes = treeVolumeDescriptorCodec.encode(tvd).require.toByteArray
    assert(bytes.length == 2048)
    buf.put(bytes)
  }
}
