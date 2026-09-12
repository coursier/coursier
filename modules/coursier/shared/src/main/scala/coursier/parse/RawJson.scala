package coursier.parse

import com.github.plokhotnyuk.jsoniter_scala.core._

import java.nio.charset.StandardCharsets
import java.util.Arrays

import scala.util.hashing.MurmurHash3
import scala.util.Try

// adapted from https://github.com/plokhotnyuk/jsoniter-scala/blob/209d918a030b188f064ee55505a6c47257731b4b/jsoniter-scala-macros/src/test/scala/com/github/plokhotnyuk/jsoniter_scala/macros/JsonCodecMakerSpec.scala#L645-L666
final case class RawJson(value: Array[Byte]) {
  override lazy val hashCode: Int        = MurmurHash3.arrayHash(value)
  override def equals(obj: Any): Boolean = obj match {
    case that: RawJson => Arrays.equals(value, that.value)
    case _             => false
  }
  override def toString: String =
    Try(new String(value, StandardCharsets.UTF_8))
      .toOption
      .getOrElse(value.toString)
}

object RawJson {

  implicit val codec: JsonValueCodec[RawJson] = new JsonValueCodec[RawJson] {
    def decodeValue(in: JsonReader, default: RawJson): RawJson =
      new RawJson(in.readRawValAsBytes())
    def encodeValue(x: RawJson, out: JsonWriter): Unit =
      out.writeRawVal(x.value)
    val nullValue: RawJson =
      new RawJson(new Array[Byte](0))
  }

  val emptyObj: RawJson =
    RawJson("{}".getBytes(StandardCharsets.UTF_8))
}
