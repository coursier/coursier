package coursier.install

import java.io.ByteArrayOutputStream

import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._
import coursier.parse.RawJson

private[coursier] object Codecs {

  /** Reads a JSON object, keeping its content unparsed */
  implicit val rawJsonObject: JsonValueCodec[RawJson] =
    new JsonValueCodec[RawJson] {
      def decodeValue(in: JsonReader, default: RawJson): RawJson =
        if (in.isNextToken('{')) {
          in.rollbackToken()
          RawJson(compact(in.readRawValAsBytes()))
        }
        else
          in.decodeError("expected JSON object")
      def encodeValue(x: RawJson, out: JsonWriter): Unit =
        out.writeRawVal(x.value)
      def nullValue: RawJson =
        null
    }

  implicit val rawJsonObjectMap: JsonValueCodec[Map[String, RawJson]] =
    JsonCodecMaker.make

  /** Strips the insignificant whitespace off a (valid) JSON document
    *
    * App descriptors read from channels are kept around as raw bytes, and compared byte-per-byte to
    * decide whether an installed launcher is up-to-date. Normalizing them here keeps that
    * comparison insensitive to the formatting of the channel file.
    */
  private[install] def compact(input: Array[Byte]): Array[Byte] = {
    val b        = new ByteArrayOutputStream(input.length)
    var idx      = 0
    var inString = false
    var escaped  = false
    while (idx < input.length) {
      val c = input(idx)
      if (inString) {
        // no need to look at the UTF-8 continuation bytes, those are all negative as signed bytes
        if (escaped) escaped = false
        else if (c == '\\') escaped = true
        else if (c == '"') inString = false
        b.write(c.toInt)
      }
      else if (c == '"') {
        inString = true
        b.write(c.toInt)
      }
      else if (c != ' ' && c != '\t' && c != '\n' && c != '\r')
        b.write(c.toInt)
      idx += 1
    }
    b.toByteArray
  }

  // hex dumps end up in user-facing error messages, keep those readable
  private val readerConfig = ReaderConfig.withAppendHexDumpToParseException(false)

  private[install] def read[T](input: String)(implicit
    codec: JsonValueCodec[T]
  )
    : Either[String, T] =
    try Right(readFromString(input, readerConfig)(codec))
    catch {
      case e: JsonReaderException =>
        Left(Option(e.getMessage).getOrElse(e.toString))
    }

  private[install] def write[T](value: T)(implicit codec: JsonValueCodec[T]): String =
    writeToString(value)(codec)

  private[install] def writeIndented[T](value: T)(implicit codec: JsonValueCodec[T]): String =
    writeToString(value, WriterConfig.withIndentionStep(2))(codec)

}
