package coursier.docker.vm.iso

import scodec._
import scodec.bits.ByteVector
import scodec.codecs._

import scala.compiletime.ops.int._

final case class ZeroPaddedByteArray[N <: Int] private (value: Array[Byte])

object ZeroPaddedByteArray {
  def create[N <: Int](value: Array[Byte])(implicit n: ValueOf[N]): ZeroPaddedByteArray[N] = {
    assert(value.length <= n.value)
    ZeroPaddedByteArray(value)
  }

  def empty[N <: Int]: ZeroPaddedByteArray[N] =
    ZeroPaddedByteArray(Array.emptyByteArray)

  implicit def codec[N <: Int](implicit n: ValueOf[N]): Codec[ZeroPaddedByteArray[N]] =
    bytes(n.value).xmap(
      bv => {
        val array  = bv.toArray
        val endsAt = array.indexOf(0: Byte)
        val len    = if (endsAt >= 0) endsAt else n.value
        ZeroPaddedByteArray(array.take(len))
      },
      s => {
        val len = s.value.length.min(n.value)
        ByteVector(s.value.take(len)) ++
          ByteVector.fill(n.value - len)(0: Byte)
      }
    )
}
