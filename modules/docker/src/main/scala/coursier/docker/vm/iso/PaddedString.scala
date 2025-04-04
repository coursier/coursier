package coursier.docker.vm.iso

import scodec._
import scodec.bits.ByteVector
import scodec.codecs._

import java.nio.charset.StandardCharsets

import scala.compiletime.ops.int._

final case class PaddedString[N <: Int, P <: Int] private (value: String)

object PaddedString {
  def create[N <: Int, P <: Int](value: String)(implicit n: ValueOf[N]): PaddedString[N, P] = {
    assert(value.length <= n.value)
    PaddedString(value)
  }

  def empty[N <: Int, P <: Int]: PaddedString[N, P] =
    PaddedString("")

  implicit def codec[N <: Int, P <: Int](implicit
    n: ValueOf[N],
    p: ValueOf[P]
  ): Codec[PaddedString[N, P]] =
    bytes(n.value).xmap(
      bv => {
        val array  = bv.toArray
        val endsAt = array.indexOf(p.value.toByte)
        val len    = if (endsAt >= 0) endsAt else n.value
        PaddedString(new String(array, 0, len, StandardCharsets.US_ASCII).take(n.value))
      },
      s => {
        val len = s.value.length.min(n.value)
        ByteVector(s.value.take(len).getBytes(StandardCharsets.US_ASCII)) ++
          ByteVector.fill(n.value - len)(p.value.toByte)
      }
    )
}
