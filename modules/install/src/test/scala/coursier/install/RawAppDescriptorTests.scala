package coursier.install

import java.nio.charset.StandardCharsets

import scala.util.Try

import cats.data.Validated
import com.github.plokhotnyuk.jsoniter_scala.core.{JsonValueCodec, readFromString}
import com.github.plokhotnyuk.jsoniter_scala.macros.JsonCodecMaker
import coursier.parse.RawJson
import coursier.version.{Version, VersionInterval}
import utest._

object RawAppDescriptorTests extends TestSuite {

  private def readResource(path: String): String = {
    val is = Option(getClass.getResourceAsStream(path))
      .getOrElse(sys.error(s"Resource $path not found"))
    new String(is.readAllBytes(), StandardCharsets.UTF_8)
  }

  private sealed abstract class NormalizedJson extends Product with Serializable
  private final case class JsonObject(values: Seq[(String, NormalizedJson)]) extends NormalizedJson
  private final case class JsonArray(values: Seq[NormalizedJson])            extends NormalizedJson
  private final case class JsonString(value: String)                         extends NormalizedJson
  private final case class JsonBoolean(value: Boolean)                       extends NormalizedJson
  private case object JsonNull                                               extends NormalizedJson

  private val mapCodec: JsonValueCodec[Map[String, RawJson]] = JsonCodecMaker.make
  private val listCodec: JsonValueCodec[List[RawJson]]       = JsonCodecMaker.make
  private val stringCodec: JsonValueCodec[String]            = JsonCodecMaker.make
  private val booleanCodec: JsonValueCodec[Boolean]          = JsonCodecMaker.make

  private def normalized(input: String): NormalizedJson = {
    def parse[T: com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec]: Option[T] =
      Try(readFromString[T](input)).toOption

    if (input.trim == "null") JsonNull
    else
      parse(mapCodec)
        .map { obj =>
          JsonObject(
            obj.toSeq
              .map { case (key, value) => key -> normalized(value.toString) }
              .sortBy(_._1)
          )
        }
        .orElse {
          parse(listCodec).map(values =>
            JsonArray(values.map(value => normalized(value.toString)))
          )
        }
        .orElse(parse(stringCodec).map(JsonString))
        .orElse(parse(booleanCodec).map(JsonBoolean))
        .getOrElse(sys.error(s"Error parsing JSON: $input"))
  }

  private def checkGolden[T](
    path: String,
    decode: String => Either[String, T]
  )(
    encode: T => String
  ): Unit = {
    val path0   = s"/golden/install/$path"
    val content = readResource(path0)
    val value = decode(content) match {
      case Left(error)  => sys.error(s"Error decoding $path0: $error")
      case Right(value) => value
    }

    val actualJson   = normalized(encode(value))
    val expectedJson = normalized(content)
    if (actualJson != expectedJson) {
      pprint.err.log(expectedJson)
      pprint.err.log(actualJson)
      Thread.sleep(2000L)
    }
    assert(actualJson == expectedJson)
  }

  val it1 = VersionInterval(Some(Version("2.0.1")), Some(Version("2.1.0")), true, true)
  val it2 = VersionInterval(Some(Version("2.1.2")), Some(Version("2.3.0")), true, false)
  val it3 = VersionInterval(Some(Version("3.0.0")), None, true, true)
  val it4 = VersionInterval(Some(Version("2.2.0")), Some(Version("3.2.0")), false, false)

  val vo1 = VersionOverride(it1)
  val vo2 = VersionOverride(it2)
  val vo3 = VersionOverride(it3)
  val vo4 = VersionOverride(it4)

  val tests: Tests = Tests {
    test("validate disjoint version intervals") {
      val versionOverrides = Seq(vo1, vo2, vo3)
      val validated        = RawAppDescriptor.validateRanges(versionOverrides)
      assert(validated == Validated.validNel(versionOverrides))
    }

    test("invalidate overlapping version intervals") {
      val versionOverrides = Seq(vo1, vo2, vo4)
      val validated        = RawAppDescriptor.validateRanges(versionOverrides)
      assertMatch(validated) { case Validated.Invalid(_) => () }
    }

    test("RawAppDescriptor JSON golden files") {
      test("minimal") {
        checkGolden("raw-app-descriptor/minimal.json", RawAppDescriptor.parse)(_.repr)
      }
      test("full") {
        checkGolden("raw-app-descriptor/full.json", RawAppDescriptor.parse)(_.repr)
      }
      test("version-overrides") {
        checkGolden("raw-app-descriptor/version-overrides.json", RawAppDescriptor.parse)(_.repr)
      }
    }

    test("RawSource JSON golden files") {
      test("inline") {
        checkGolden("raw-source/inline.json", RawSource.parse)(_.repr)
      }
      test("url") {
        checkGolden("raw-source/url.json", RawSource.parse)(_.repr)
      }
      test("github") {
        checkGolden("raw-source/github.json", RawSource.parse)(_.repr)
      }
    }
  }
}
