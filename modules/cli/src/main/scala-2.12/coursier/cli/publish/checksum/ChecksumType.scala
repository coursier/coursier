package coursier.cli.publish.checksum

import java.util.Locale

/**
  * A type of checksum.
  *
  * @param name
  * @param extension: extension of this checksum type, without a prefix `"."`
  * @param size: size of hexadecimal string representation - size in bits is this one times 4
  */
sealed abstract class ChecksumType(
  val name: String,
  val extension: String,
  val size: Int
) extends Product with Serializable

object ChecksumType {
  case object SHA1 extends ChecksumType("sha-1", "sha1", 40)
  case object MD5 extends ChecksumType("md5", "md5", 32)

  val all = Seq(SHA1, MD5)
  val map = all.map(c => c.name -> c).toMap

  def parse(s: String): Either[String, ChecksumType] =
    map
      .get(s.toLowerCase(Locale.ROOT))
      .toRight(s"Unrecognized checksum: $s")
}
