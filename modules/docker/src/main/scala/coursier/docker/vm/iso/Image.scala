package coursier.docker.vm.iso

import java.nio.ByteBuffer

object Image {

  def generate(
    volumeId: String,
    files: Seq[(String, Array[Byte])]
  ): Array[Byte] = {

    val fileData = files.map {
      case (name, content) =>
        name -> new Data(content)
    }
    val fileDataMap = fileData.toMap

    lazy val pathTable = NoDirPathTable(rootDir)

    lazy val rootDir: Directory = new Directory(() => rootEntries)
    lazy val rootEntries        =
      Seq(
        new DotDirectoryEntry(rootDir),
        new DotDotDirectoryEntry(rootDir, None)
      ) ++
        files
          .map(_._1)
          .sorted
          .map(name => new FileDirectoryEntry(name, fileDataMap(name)))

    val records =
      Seq(
        new EmptyRecords(16),
        PrimaryVolumeDescriptor(
          new DotDirectoryEntry(rootDir),
          pathTable,
          LastItem,
          volumeIdentifier = volumeId
        ),
        VolumeDescriptorTerminator,
        pathTable.littleEndian,
        pathTable.bigEndian,
        rootDir
      ) ++
        fileData.map(_._2) ++
        Seq(LastItem)

    val indexList = records.scanLeft(0)((idx, rec) => idx + rec.lengthInSectors)
    val indices   = Indices(records.zip(indexList).toMap)

    val buf = ByteBuffer.allocate(indexList.last * 2048)
    for (record <- records)
      record.write(buf, indices)

    val sectorCount = indices(LastItem)
    if (sectorCount < 176)
      buf.array() ++ Array.fill((176 - sectorCount) * 2048)(0: Byte)
    else
      buf.array()
  }

}
