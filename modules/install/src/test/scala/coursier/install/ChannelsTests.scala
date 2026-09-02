package coursier.install

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import coursier.cache.FileCache
import coursier.parse.DependencyParser
import coursier.util.Task
import utest._

import scala.jdk.CollectionConverters._

object ChannelsTests extends TestSuite {

  private def delete(d: Path): Unit = {
    if (Files.isDirectory(d)) {
      var s: java.util.stream.Stream[Path] = null
      try {
        s = Files.list(d)
        s.iterator().asScala.toVector.foreach(delete)
      }
      finally if (s != null)
          s.close()
    }
    Files.deleteIfExists(d)
  }

  private def withTempDir[T](f: Path => T): T = {
    val tmpDir = Files.createTempDirectory("coursier-channels-test")
    try f(tmpDir)
    finally delete(tmpDir)
  }

  private val fooDescriptor =
    """{
      |  "repositories": ["central"],
      |  "dependencies": ["org.foo:foo:1.2.3"],
      |  "mainClass": "foo.Main"
      |}""".stripMargin
  private val barDescriptor =
    """{
      |  "repositories": ["central"],
      |  "dependencies": ["org.bar::bar:latest.release"]
      |}""".stripMargin

  private def channelFileContent =
    s"""{
       |  "foo": $fooDescriptor,
       |  "bar": $barDescriptor
       |}
       |""".stripMargin

  private def write(path: Path, content: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
  }

  private def fooDependency =
    DependencyParser.javaOrScalaDependencyParams("org.foo:foo:1.2.3") match {
      case Left(err)       => sys.error(err)
      case Right((dep, _)) => dep
    }

  val tests = Tests {

    test("url channel") {
      withTempDir { tmpDir =>
        val channelFile = tmpDir.resolve("channel.json")
        write(channelFile, channelFileContent)

        val cache   = FileCache[Task]().withLocation(tmpDir.resolve("cache").toFile)
        val channel = Channel.url(channelFile.toUri.toASCIIString)
        assert(channel.url.startsWith("file:"))
        val channels = Channels().withChannels(Seq(channel)).withCache(cache)

        implicit val ec = cache.ec

        // find
        val fooDataOpt = channels.find("foo").unsafeRun(wrapExceptions = true)
        assert(fooDataOpt.exists(_.channel == channel))
        val parsed = RawAppDescriptor.parse(fooDataOpt.get.strData)
        assert(parsed.exists(_.mainClass.contains("foo.Main")))
        assert(parsed.exists(_.dependencies == List("org.foo:foo:1.2.3")))

        // not found
        val bazDataOpt = channels.find("baz").unsafeRun(wrapExceptions = true)
        assert(bazDataOpt.isEmpty)

        // app descriptor
        val appInfo = channels.appDescriptor("foo").unsafeRun(wrapExceptions = true)
        assert(appInfo.appDescriptor.dependencies == Seq(fooDependency))
        assert(appInfo.source.channel == channel)
        val notFound =
          channels.appDescriptor("baz").attempt.unsafeRun(wrapExceptions = true)
        assert(notFound.left.exists(_.isInstanceOf[Channels.AppNotFound]))

        // search
        val all = channels.searchAppName(Nil).unsafeRun(wrapExceptions = true)
        assert(all == List("bar", "foo"))
        val some = channels.searchAppName(Seq("fo")).unsafeRun(wrapExceptions = true)
        assert(some == List("foo"))
      }
    }

    test("url and directory channels") {
      withTempDir { tmpDir =>
        val channelFile = tmpDir.resolve("channel.json")
        write(channelFile, channelFileContent)
        val dir = tmpDir.resolve("dir-channel")
        write(dir.resolve("baz.json"), barDescriptor)
        // shadowed by the URL channel, that comes first
        write(dir.resolve("foo.json"), barDescriptor)

        val cache      = FileCache[Task]().withLocation(tmpDir.resolve("cache").toFile)
        val urlChannel = Channel.url(channelFile.toUri.toASCIIString)
        val dirChannel = Channel.FromDirectory(dir)
        val channels   = Channels().withChannels(Seq(urlChannel, dirChannel)).withCache(cache)

        implicit val ec = cache.ec

        val foo = channels.find("foo").unsafeRun(wrapExceptions = true)
        assert(foo.exists(_.channel == urlChannel))
        val baz = channels.find("baz").unsafeRun(wrapExceptions = true)
        assert(baz.exists(_.channel == dirChannel))
        val all = channels.searchAppName(Nil).unsafeRun(wrapExceptions = true)
        assert(all == List("bar", "baz", "foo", "foo"))
      }
    }

    test("unreachable url channel") {
      withTempDir { tmpDir =>
        val cache    = FileCache[Task]().withLocation(tmpDir.resolve("cache").toFile)
        val channel  = Channel.url(tmpDir.resolve("missing.json").toUri.toASCIIString)
        val channels = Channels().withChannels(Seq(channel)).withCache(cache)

        implicit val ec = cache.ec

        val res = channels.find("foo").attempt.unsafeRun(wrapExceptions = true)
        assert(res.left.exists(_.isInstanceOf[Channels.ErrorFetchingChannel]))
        val res0 = channels.appDescriptor("foo").attempt.unsafeRun(wrapExceptions = true)
        assert(res0.left.exists(_.isInstanceOf[Channels.ChannelsException]))
        val res1 = channels.searchAppName(Nil).attempt.unsafeRun(wrapExceptions = true)
        assert(res1.left.exists(_.isInstanceOf[Channels.ErrorFetchingChannel]))
      }
    }

    test("malformed url channel") {
      withTempDir { tmpDir =>
        val channelFile = tmpDir.resolve("channel.json")
        write(channelFile, """{ "foo": [] }""")
        val cache    = FileCache[Task]().withLocation(tmpDir.resolve("cache").toFile)
        val channel  = Channel.url(channelFile.toUri.toASCIIString)
        val channels = Channels().withChannels(Seq(channel)).withCache(cache)

        implicit val ec = cache.ec

        val res = channels.find("foo").attempt.unsafeRun(wrapExceptions = true)
        assert(res.left.exists(_.isInstanceOf[Channels.ErrorDecodingChannel]))
      }
    }

    test("default channels are URL-based") {
      val default = Channels.defaultChannels
      val contrib = Channels.contribChannels
      assert(default.nonEmpty)
      assert(contrib.nonEmpty)
      assert(default.forall(_.isInstanceOf[Channel.FromUrl]))
      assert(contrib.forall(_.isInstanceOf[Channel.FromUrl]))
      assert(default.forall(_.repr.startsWith("https://raw.githubusercontent.com/coursier/apps/")))
      assert(contrib.forall(_.repr.startsWith("https://raw.githubusercontent.com/coursier/apps/")))
      assert(default != contrib)
    }

    test("parse") {
      test("url") {
        val url = "https://raw.githubusercontent.com/coursier/apps/main/apps.json"
        val res = Channel.parse(url)
        assert(res == Right(Channel.FromUrl(url)))
      }
      test("github url") {
        val res = Channel.parse("https://github.com/coursier/apps/blob/main/apps.json")
        val expected =
          Channel.FromUrl("https://raw.githubusercontent.com/coursier/apps/main/apps.json")
        assert(res == Right(expected))
      }
      test("gh shorthand") {
        val res = Channel.parse("gh:coursier/apps/main")
        val expected =
          Channel.FromUrl("https://raw.githubusercontent.com/coursier/apps/main/apps.json")
        assert(res == Right(expected))
      }
      test("module") {
        val res = Channel.parse("io.get-coursier:apps")
        assert(res.exists(_.isInstanceOf[Channel.FromModule]))
      }
    }
  }
}
