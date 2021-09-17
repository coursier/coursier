package coursier.publish.checksum

import java.math.BigInteger
import java.security.MessageDigest

/** A… checksum
  */
final case class Checksum(`type`: ChecksumType, value: BigInteger) {
  assert(`type`.validValue(value))
  def repr: String =
    String.format(s"%0${`type`.size}x", value)
}

object Checksum {

  def compute(`type`: ChecksumType, content: Array[Byte]): Checksum = {

    val md = MessageDigest.getInstance(`type`.name)
    md.update(content)
    val digest        = md.digest()
    val calculatedSum = new BigInteger(1, digest)

    Checksum(`type`, calculatedSum)
  }

}
