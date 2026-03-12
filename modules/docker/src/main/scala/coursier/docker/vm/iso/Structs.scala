package coursier.docker.vm.iso

import scodec._
import scodec.bits._
import scodec.codecs._
import java.nio.charset.StandardCharsets
import java.time.{LocalDateTime, ZoneOffset}

object Structs {

  final case class Header(
    `type`: Int,
    stdId: ZeroPaddedByteArray[5],
    version: Int
  )

  private def byteArray(size: Int): Codec[Array[Byte]] =
    bytes(size).xmap[Array[Byte]](_.toArray, ByteVector(_))
  private val empty = Array.fill(2048)(0: Byte)

  lazy val headerCodec: Codec[Header] =
    (uint8 :: ZeroPaddedByteArray.codec[5] :: uint8).as[Header]

  final case class PathTableStuff(
    pathTableSize: Long,
    pathTableLocation: Long,
    optPathTableLocation: ZeroPaddedByteArray[4],
    mPathTableLocation: Long,
    mOptPathTableLocation: ZeroPaddedByteArray[4]
  )

  object PathTableStuff {
    def empty = PathTableStuff(
      pathTableSize = 0,
      pathTableLocation = 0,
      optPathTableLocation = ZeroPaddedByteArray.empty,
      mPathTableLocation = 0,
      mOptPathTableLocation = ZeroPaddedByteArray.empty
    )
  }

  lazy val pathTableStuffCodec: Codec[PathTableStuff] =
    (
      bothEndiannessUInt32 ::
        uint32L ::
        ZeroPaddedByteArray.codec[4] ::
        uint32 ::
        ZeroPaddedByteArray.codec[4]
    ).as[PathTableStuff]

  private lazy val bothEndiannessUInt16: Codec[Int] =
    (uint16L :: uint16).xmap(
      _._1, // check both values are equal too?
      n => (n, n)
    )

  private lazy val bothEndiannessUInt32: Codec[Long] =
    (uint32L :: uint32).xmap(
      _._1, // check both values are equal too?
      n => (n, n)
    )

  private lazy val date17Codec: Codec[Option[LocalDateTime]] = {
    val empty   = Array.fill(17)(0: Byte)
    val pattern = raw"(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})".r
    byteArray(17).xmap[Option[LocalDateTime]](
      b =>
        if (b(0) == 0) None
        else {
          val str = new String(b, StandardCharsets.US_ASCII)
          str match {
            case pattern(year, month, day, hour, minute, second, hundredth) =>
              val dt = LocalDateTime.of(
                year.toInt,
                month.toInt,
                day.toInt,
                hour.toInt,
                minute.toInt,
                second.toInt,
                hundredth.toInt * 10000000
              )
              Some(dt)
          }
        },
      dtOpt =>
        dtOpt match {
          case None => empty
          case Some(dt) =>
            val str = "%04d%02d%02d%02d%02d%02d%02d".format(
              dt.getYear,
              dt.getMonth.getValue,
              dt.getDayOfMonth,
              dt.getHour,
              dt.getMinute,
              dt.getSecond,
              dt.getNano / 10000000
            )
            val bytes = str.getBytes(StandardCharsets.US_ASCII)
            assert(bytes.length == 16)
            bytes
        }
    )
  }

  final case class TreeVolumeDescriptor(
    header: Header,
    /* SVD only */ volumeFlags: Int,
    systemIdentifier: PaddedString[32, 0],
    volumeIdentifier: PaddedString[32, 0],
    volumeSpaceSize: Long,
    escapeSequences: PaddedString[32, 0],
    volumeSetSize: Int,
    volumeSequenceNumber: Int,
    logicalBlockSize: Int,
    pathTableStuff: PathTableStuff,
    rootDirectoryRecord: ZeroPaddedByteArray[34],
    volumeSetIdentifier: PaddedString[128, 0],
    publisherIdentifier: PaddedString[128, 32],
    dataPreparerIdentifier: PaddedString[128, 32],
    applicationIdentifier: PaddedString[128, 32],
    copyrightFileIdentifier: PaddedString[37, 0],
    abstractIdentifier: PaddedString[37, 0],
    bibliographicIdentifier: PaddedString[37, 0],
    volumeCreationDate: Option[LocalDateTime],
    volumeModificationDate: Option[LocalDateTime],
    volumeExpirationDate: Option[LocalDateTime],
    volumeEffectiveDate: Option[LocalDateTime],
    fileStructureVersion: Int,
    applicationUse: ZeroPaddedByteArray[512]
  )

  object TreeVolumeDescriptor {
    def empty = TreeVolumeDescriptor(
      header = Header(
        `type` = 0,
        stdId = ZeroPaddedByteArray.empty,
        version = 0
      ),
      volumeFlags = 0,
      systemIdentifier = PaddedString.empty,
      volumeIdentifier = PaddedString.empty,
      volumeSpaceSize = 0,
      escapeSequences = PaddedString.empty,
      volumeSetSize = 1,
      volumeSequenceNumber = 1,
      logicalBlockSize = 0,
      pathTableStuff = PathTableStuff(
        pathTableSize = 0,
        pathTableLocation = 0,
        optPathTableLocation = ZeroPaddedByteArray.empty,
        mPathTableLocation = 0,
        mOptPathTableLocation = ZeroPaddedByteArray.empty
      ),
      rootDirectoryRecord = ZeroPaddedByteArray.empty,
      volumeSetIdentifier = PaddedString.empty,
      publisherIdentifier = PaddedString.empty,
      dataPreparerIdentifier = PaddedString.empty,
      applicationIdentifier = PaddedString.empty,
      copyrightFileIdentifier = PaddedString.empty,
      abstractIdentifier = PaddedString.empty,
      bibliographicIdentifier = PaddedString.empty,
      volumeCreationDate = None,
      volumeModificationDate = None,
      volumeExpirationDate = None,
      volumeEffectiveDate = None,
      fileStructureVersion = 0,
      applicationUse = ZeroPaddedByteArray.empty
    )
  }

  lazy val treeVolumeDescriptorCodec: Codec[TreeVolumeDescriptor] =
    (
      headerCodec ::
        uint8 ::
        PaddedString.codec[32, 0] ::
        PaddedString.codec[32, 0] ::
        bytes(8).unit(ByteVector.view(empty, 0, 8)) ::
        bothEndiannessUInt32 ::
        PaddedString.codec[32, 0] ::
        bothEndiannessUInt16 ::
        bothEndiannessUInt16 ::
        bothEndiannessUInt16 ::
        pathTableStuffCodec ::           // 156?
        ZeroPaddedByteArray.codec[34] :: // 190?
        PaddedString.codec[128, 0] ::
        PaddedString.codec[128, 32] ::
        PaddedString.codec[128, 32] ::
        PaddedString.codec[128, 32] ::
        PaddedString.codec[37, 0] ::
        PaddedString.codec[37, 0] ::
        PaddedString.codec[37, 0] ::
        date17Codec ::
        date17Codec ::
        date17Codec ::
        date17Codec ::
        uint8 ::
        uint8.unit(0) ::
        ZeroPaddedByteArray.codec[512] ::
        bytes(653).unit(ByteVector.view(empty, 0, 653))
    ).as[TreeVolumeDescriptor]

  final case class PathTableRecord(
    lenDi: Int,
    earLen: Int,
    locationOfExtent: Long,
    parentDirNum: Int
  )

  object PathTableRecord {
    def empty = PathTableRecord(
      lenDi = 0,
      earLen = 0,
      locationOfExtent = 0,
      parentDirNum = 0
    )
  }

  lazy val pathTableRecordCodec: Codec[PathTableRecord] =
    (
      uint8 ::
        uint8 ::
        uint32 ::
        uint16
        /* Then:
         *	guchar directory_identifier[len_di];
         *  If len_di is an odd number:
         *	guchar padding;
         */
    ).as[PathTableRecord]
  lazy val pathTableRecordCodecL: Codec[PathTableRecord] =
    (
      uint8L ::
        uint8L ::
        uint32L ::
        uint16L
        /* Then:
         *	guchar directory_identifier[len_di];
         *  If len_di is an odd number:
         *	guchar padding;
         */
    ).as[PathTableRecord]

  final case class Iso915Date(
    year: Int,
    month: Int,
    day: Int,
    hour: Int,
    minute: Int,
    second: Int,
    offsetFromGmt: Int
  )

  object Iso915Date {
    def empty = Iso915Date(
      year = 0,
      month = 0,
      day = 0,
      hour = 0,
      minute = 0,
      second = 0,
      offsetFromGmt = 0
    )
  }

  lazy val iso915DateCodec: Codec[Iso915Date] =
    (
      uint8 ::
        uint8 ::
        uint8 ::
        uint8 ::
        uint8 ::
        uint8 ::
        uint8
    ).as[Iso915Date]

  object DirectoryRecordFlag {
    def hidden         = 1
    def directory      = 2
    def associatedFile = 4
    def record         = 8
    def protection     = 16
    def multiExtent    = 128
  }

  final case class DirectoryRecord(
    lenDr: Int,
    earLen: Int,
    location: Long,
    dataLen: Long,
    recordingDate: Iso915Date,
    fileFlags: Int,
    fileUnitSize: Int,
    interleaveGapSize: Int,
    volumeSequenceNumber: Int,
    lenFi: Int
  )

  object DirectoryRecord {
    def empty = DirectoryRecord(
      lenDr = 0,
      earLen = 0,
      location = 0,
      dataLen = 0,
      recordingDate = Iso915Date.empty.copy(
        year = 70,
        month = 1,
        day = 1
      ),
      fileFlags = 0,
      fileUnitSize = 0,
      interleaveGapSize = 0,
      volumeSequenceNumber = 0,
      lenFi = 0
    )
  }

  lazy val directoryRecordCodec: Codec[DirectoryRecord] =
    (
      uint8 ::
        uint8 ::
        bothEndiannessUInt32 ::
        bothEndiannessUInt32 ::
        iso915DateCodec ::
        uint8 ::
        uint8 ::
        uint8 ::
        bothEndiannessUInt16 ::
        uint8
        /* Then:
         *	guchar file_identifier[len_fi];
         *  If len_fi is an even number:
         *	guchar padding;
         *
	     *	guchar system_use[len_su];
         */
    ).as[DirectoryRecord]

  def PrimaryType: Int       = 1
  def SupplementaryType: Int = 2
  def SetTerminatorType: Int = 255

  def Version              = 1
  def FileStructureVersion = 1

  def Directory = 2

  lazy val cd001 = "CD001".getBytes(StandardCharsets.US_ASCII)

  lazy val defaultDate = LocalDateTime.ofEpochSecond(0L, 0, ZoneOffset.UTC)

}
