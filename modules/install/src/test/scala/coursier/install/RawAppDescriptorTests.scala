package coursier.install

import java.nio.charset.StandardCharsets

import argonaut.{EncodeJson, Json, Parse}
import cats.data.Validated
import coursier.version.{Version, VersionInterval}
import utest._

object RawAppDescriptorTests extends TestSuite {

  private implicit val rawSourceEncoder: EncodeJson[RawSource] =
    RawSource.encoder

  private def readResource(path: String): String = {
    val is = Option(getClass.getResourceAsStream(path))
      .getOrElse(sys.error(s"Resource $path not found"))
    try {
      val baos = new java.io.ByteArrayOutputStream
      val buf  = Array.ofDim[Byte](16384)
      var read = -1
      while ({ read = is.read(buf); read >= 0 })
        baos.write(buf, 0, read)
      new String(baos.toByteArray, StandardCharsets.UTF_8)
    }
    finally is.close()
  }

  private def parseJson(path: String, content: String): Json =
    Parse.parse(content) match {
      case Left(error) => sys.error(s"Error parsing $path as JSON: $error")
      case Right(json) => json
    }

  private def normalized(json: Json): Any =
    json.obj match {
      case Some(obj) =>
        obj.toList
          .map { case (key, value) => key -> normalized(value) }
          .sortBy(_._1)
      case None =>
        json.array match {
          case Some(values) => values.map(normalized)
          case None         => json.nospaces
        }
    }

  private def checkGolden[T](path: String, decode: String => Either[String, T])(
    implicit encoder: EncodeJson[T]
  ): Unit = {
    val content = readResource(path)
    val value = decode(content) match {
      case Left(error)  => sys.error(s"Error decoding $path: $error")
      case Right(value) => value
    }

    val actualJson   = normalized(parseJson(path, encoder.encode(value).nospaces))
    val expectedJson = normalized(parseJson(path, content))
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
      val goldenFiles = Seq(
        "/golden/install/raw-app-descriptor/minimal.json",
        "/golden/install/raw-app-descriptor/full.json",
        "/golden/install/raw-app-descriptor/version-overrides.json"
      )

      for (path <- goldenFiles)
        checkGolden(path, RawAppDescriptor.parse)
    }

    test("RawSource JSON golden files") {
      val goldenFiles = Seq(
        "/golden/install/raw-source/inline.json",
        "/golden/install/raw-source/url.json",
        "/golden/install/raw-source/github.json"
      )

      for (path <- goldenFiles)
        checkGolden(path, RawSource.parse)
    }
  }
}
