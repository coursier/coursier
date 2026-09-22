package coursier.install

import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

import cats.data.Validated
import coursier.Fetch
import coursier.cache.FileCache
import coursier.cache.internal.FileUtil
import coursier.core.{Dependency, Module, ModuleName, Organization}
import coursier.install.Codecs.rawJsonObjectMap
import coursier.parse.RawJson
import coursier.version.{Version, VersionConstraint, VersionInterval}
import utest._

import scala.jdk.CollectionConverters._
import scala.util.Using

object RawAppDescriptorTests extends TestSuite {

  private def readResource(path: String): String = {
    val is = Option(getClass.getResourceAsStream(path))
      .getOrElse(sys.error(s"Resource $path not found"))
    new String(is.readAllBytes(), StandardCharsets.UTF_8)
  }

  /** JSON content, with object fields sorted, so that two JSON documents that only differ by the
    * order of their fields compare equal
    */
  private def normalized(json: ujson.Value): Any =
    json match {
      case obj: ujson.Obj =>
        obj.value.toList
          .map { case (key, value) => key -> normalized(value) }
          .sortBy(_._1)
      case arr: ujson.Arr =>
        arr.value.toList.map(normalized)
      case other =>
        other.render()
    }

  private def normalized(content: String): Any =
    normalized(ujson.read(content))

  /** Checks that re-encoding a JSON object neither lost nor altered anything
    *
    * @param dropped
    *   fields coursier doesn't model, expected not to survive re-encoding
    * @param addedValue
    *   value expected for a field that re-encoding adds, if adding it is expected at all
    * @param ignored
    *   fields compared separately by the caller
    */
  private def checkReEncoded(
    context: String,
    from: ujson.Obj,
    to: ujson.Obj,
    dropped: Set[String],
    addedValue: String => Option[ujson.Value],
    ignored: Set[String] = Set.empty
  ): Unit = {
    val fromKeys = from.value.keySet.toSet
    val toKeys   = to.value.keySet.toSet

    val lost = fromKeys -- toKeys -- dropped
    if (lost.nonEmpty)
      sys.error(s"$context: fields lost when re-encoding: ${lost.toVector.sorted.mkString(", ")}")

    for (key <- (toKeys -- fromKeys).toVector.sorted)
      addedValue(key) match {
        case None =>
          sys.error(s"$context: unexpected field $key added when re-encoding")
        case Some(expected) if normalized(to(key)) != normalized(expected) =>
          sys.error(
            s"$context: field $key added as ${to(key).render()} rather than ${expected.render()}"
          )
        case Some(_) =>
      }

    for (key <- (fromKeys.intersect(toKeys) -- ignored).toVector.sorted)
      if (normalized(from(key)) != normalized(to(key)))
        sys.error(
          s"$context: field $key changed from ${from(key).render()} to ${to(key).render()}"
        )
  }

  private def channelVersion = "1.0.70"

  /** The app descriptors of one of the JAR-based `io.get-coursier` channels, by app name
    *
    * The JAR is fetched from Maven Central through the coursier cache.
    */
  private def channelDescriptors(channel: String): Map[String, String] = {
    val dep = Dependency(
      Module(Organization("io.get-coursier"), ModuleName(channel), Map.empty),
      VersionConstraint(channelVersion)
    )
    val jars = Fetch(FileCache()).addDependencies(dep).run()
    val jar  = jars match {
      case Seq(jar) => jar
      case other    => sys.error(s"Expected a single JAR for $dep, got $other")
    }
    Using.resource(new ZipFile(jar)) { zf =>
      zf.entries()
        .asScala
        .filter(ent => !ent.isDirectory && ent.getName.endsWith(".json"))
        .map { ent =>
          val content = FileUtil.readFully(zf.getInputStream(ent))
          ent.getName.stripSuffix(".json") -> new String(content, StandardCharsets.UTF_8)
        }
        .toMap
    }
  }

  private def checkGolden[T](
    path: String,
    decode: String => Either[String, T],
    encode: T => String
  ): Unit = {
    val content = readResource(path)
    val value   = decode(content) match {
      case Left(error)  => sys.error(s"Error decoding $path: $error")
      case Right(value) => value
    }

    val actualJson   = normalized(encode(value))
    val expectedJson = normalized(content)
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
        checkGolden(path, RawAppDescriptor.parse, (d: RawAppDescriptor) => d.repr)
    }

    test("RawSource JSON golden files") {
      val goldenFiles = Seq(
        "/golden/install/raw-source/inline.json",
        "/golden/install/raw-source/url.json",
        "/golden/install/raw-source/github.json"
      )

      for (path <- goldenFiles)
        checkGolden(path, RawSource.parse, (s: RawSource) => s.repr)
    }

    // io.get-coursier:apps and io.get-coursier:apps-contrib are the former, JAR-based app
    // channels. No more versions of those are going to be cut, so 1.0.70 is the final one, and
    // can be pinned here. The exact encoding is pinned by the golden files above; this checks it
    // against every real world app descriptor coursier ever published.
    test("published app channels") {

      // fields coursier doesn't model, that re-encoding is expected to drop
      val notModelled = Set("deprecated", "descriptor-comments", "sharedLoaderDependencies")
      // fields written out even when the descriptor leaves them out
      val writtenDefaults =
        Map[String, ujson.Value](
          "properties"   -> ujson.Obj(),
          "launcherType" -> ujson.Str("bootstrap")
        )

      var count = 0

      for {
        channel                <- Seq("apps", "apps-contrib")
        (name, descriptorJson) <- channelDescriptors(channel).toVector.sortBy(_._1)
      } {
        val app  = s"$channel/$name"
        val desc = RawAppDescriptor.parse(descriptorJson) match {
          case Left(error)  => sys.error(s"Error parsing $app: $error")
          case Right(value) => value
        }

        // re-encoding must lose nothing the descriptor says…
        val encoded = desc.repr
        val from    = ujson.read(descriptorJson).obj
        val to      = ujson.read(encoded).obj
        checkReEncoded(
          app,
          from,
          to,
          dropped = notModelled,
          addedValue = writtenDefaults.get,
          ignored = Set("versionOverrides")
        )

        def versionOverrides(obj: ujson.Obj) =
          obj.value.get("versionOverrides").map(_.arr.toVector).getOrElse(Vector.empty)
        val fromOverrides = versionOverrides(from)
        val toOverrides   = versionOverrides(to)
        assert(fromOverrides.length == toOverrides.length)
        for (((f, t), idx) <- fromOverrides.zip(toOverrides).zipWithIndex)
          checkReEncoded(
            s"$app versionOverrides[$idx]",
            f.obj,
            t.obj,
            dropped = Set.empty,
            // absent version override fields are written out as null
            addedValue = _ => Some(ujson.Null)
          )

        // …and must be a fixed point, so that a launcher installed from it doesn't get
        // needlessly re-installed later on
        assert(RawAppDescriptor.parse(encoded) == Right(desc))

        count += 1
      }

      assert(count == 109)
    }

    test("channel JSON golden file") {
      // channel files are read as maps of app name to raw JSON, whose values are
      // parsed as app descriptors later on
      val path    = "/golden/install/channel/apps.json"
      val content = readResource(path)
      val map     = Codecs.read[Map[String, RawJson]](content) match {
        case Left(error)  => sys.error(s"Error decoding $path: $error")
        case Right(value) => value
      }

      assert(map.keySet == Set("echo", "scalafmt"))

      // the raw JSON kept for each app must be the very JSON object found in the channel file,
      // stripped of its insignificant whitespace
      val expected = normalized(content).asInstanceOf[List[(String, Any)]].toMap
      for ((name, rawJson) <- map) {
        val descriptorJson = new String(rawJson.value, StandardCharsets.UTF_8)
        assert(normalized(descriptorJson) == expected(name))
        assert(!descriptorJson.exists(c => c == ' ' || c == '\n'))
        // and it must parse as an app descriptor
        assert(RawAppDescriptor.parse(descriptorJson).isRight)
      }
    }

    test("compact") {
      def compacted(input: String): String =
        new String(Codecs.compact(input.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8)

      assert(compacted("""{ "a" : [ 1, 2 ] , "b" : null }""") == """{"a":[1,2],"b":null}""")
      // whitespace and escapes inside strings are kept as is
      assert(compacted("""{ "a b" : "c d" }""") == """{"a b":"c d"}""")
      assert(compacted("""{ "a" : "b\\" , "c" : "d\" e" }""") == """{"a":"b\\","c":"d\" e"}""")
      // non-ASCII characters go through untouched
      assert(compacted("""{ "é" : "é" }""") == """{"é":"é"}""")
    }

    test("reject non-object values in channel files") {
      val res = Codecs.read[Map[String, RawJson]]("""{"echo": 2}""")
      assertMatch(res) { case Left(_) => () }
    }

    test("accept descriptors with missing or unknown fields") {
      val res = RawAppDescriptor.parse(
        """{"dependencies": ["io.get-coursier:echo:1.0.2"], "unknownField": 2}"""
      )
      assert(res == Right(RawAppDescriptor(List("io.get-coursier:echo:1.0.2"))))
      assert(RawAppDescriptor.parse("{}") == Right(RawAppDescriptor(Nil)))
    }

    test("reject sources with missing fields") {
      assertMatch(RawSource.parse("""{"channel": "inline"}""")) { case Left(_) => () }
    }

    test("reject malformed JSON") {
      assertMatch(RawAppDescriptor.parse("{")) { case Left(_) => () }
      assertMatch(RawSource.parse("nope")) { case Left(_) => () }
    }
  }
}
