package coursier.jvm

import java.nio.charset.StandardCharsets

import coursier.cache.ArchiveType
import utest._

object JvmIndexTests extends TestSuite {

  val tests = Tests {

    test("version in range") {
      val os     = "the-os"
      val arch   = "the-arch"
      val prefix = "prefix@"
      val index = JvmIndex(
        Map(os -> Map(arch -> Map(
          s"${prefix}foo" -> Map(
            "18.1"   -> "zip+https://foo.com/jdk-18.1.zip",
            "19.1.2" -> "zip+https://foo.com/jdk-19.1.2.zip",
            "19.1.3" -> "zip+https://foo.com/jdk-19.1.3.zip",
            "19.1.4" -> "zip+https://foo.com/jdk-19.1.4.zip",
            "20.1"   -> "zip+https://foo.com/jdk-20.1.zip"
          )
        ))),
        jdkNamePrefix = Some(prefix)
      )

      val entry192 =
        JvmIndexEntry(os, arch, "foo", "19.1.2", ArchiveType.Zip, "https://foo.com/jdk-19.1.2.zip")
      val entry193 =
        JvmIndexEntry(os, arch, "foo", "19.1.3", ArchiveType.Zip, "https://foo.com/jdk-19.1.3.zip")
      val entry194 =
        JvmIndexEntry(os, arch, "foo", "19.1.4", ArchiveType.Zip, "https://foo.com/jdk-19.1.4.zip")

      test {
        val res      = index.lookup("foo", "19.1", os = Some(os), arch = Some(arch))
        val expected = Right(Seq(entry192, entry193, entry194))
        assert(res == expected)
      }

      test {
        val res      = index.lookup("foo", "19.1+", os = Some(os), arch = Some(arch))
        val expected = Right(Seq(entry192, entry193, entry194))
        assert(res == expected)
      }

      test {
        val res      = index.lookup("foo", "19.1.4", os = Some(os), arch = Some(arch))
        val expected = Right(Seq(entry194))
        assert(res == expected)
      }

      test {
        val res = index.lookup("foo", "19.1.5", os = Some(os), arch = Some(arch))
        assert(res.isLeft)
      }
    }

    test("1-dot prefix") {
      val os   = "the-os"
      val arch = "the-arch"
      val index = JvmIndex(
        Map(os -> Map(arch -> Map(
          "jdk@openfoo" -> Map(
            "1.19-1" -> "zip+https://openfoo.com/jdk-19.1.zip",
            "1.19-2" -> "zip+https://openfoo.com/jdk-19.2.zip",
            "1.20-1" -> "zip+https://openfoo.com/jdk-20.1.zip",
            "1.20-2" -> "zip+https://openfoo.com/jdk-20.2.zip"
          )
        )))
      )

      val open191 = JvmIndexEntry(
        os,
        arch,
        "openfoo",
        "1.19-1",
        ArchiveType.Zip,
        "https://openfoo.com/jdk-19.1.zip"
      )
      val open192 = JvmIndexEntry(
        os,
        arch,
        "openfoo",
        "1.19-2",
        ArchiveType.Zip,
        "https://openfoo.com/jdk-19.2.zip"
      )
      val open201 = JvmIndexEntry(
        os,
        arch,
        "openfoo",
        "1.20-1",
        ArchiveType.Zip,
        "https://openfoo.com/jdk-20.1.zip"
      )
      val open202 = JvmIndexEntry(
        os,
        arch,
        "openfoo",
        "1.20-2",
        ArchiveType.Zip,
        "https://openfoo.com/jdk-20.2.zip"
      )

      test("add prefix") {
        val res      = index.lookup("openfoo", "19-2", os = Some(os), arch = Some(arch))
        val expected = Right(Seq(open192))
        assert(res == expected)
      }

      test("add prefix with interval") {
        val res      = index.lookup("openfoo", "19", os = Some(os), arch = Some(arch))
        val expected = Right(Seq(open191, open192))
        assert(res == expected)
      }

      test("accept 1-dot nonetheless") {
        val res      = index.lookup("openfoo", "1.19-2", os = Some(os), arch = Some(arch))
        val expected = Right(Seq(open192))
        assert(res == expected)
      }

      test("accept 1-dot nonetheless with interval") {
        val res      = index.lookup("openfoo", "1.19", os = Some(os), arch = Some(arch))
        val expected = Right(Seq(open191, open192))
        assert(res == expected)
      }

      test("accept just 1") {
        val res      = index.lookup("openfoo", "1", os = Some(os), arch = Some(arch))
        val expected = Right(Seq(open191, open192, open201, open202))
        assert(res == expected)
      }

      test("accept 1 plus") {
        val res      = index.lookup("openfoo", "1+", os = Some(os), arch = Some(arch))
        val expected = Right(Seq(open191, open192, open201, open202))
        assert(res == expected)
      }
    }

    test("formats") {
      val os   = "the-os"
      val arch = "the-arch"

      val tgzUrl = "https://foo.com/jdk-21.0.1.tar.gz"
      val zipUrl = "https://foo.com/jdk-21.0.2.zip"

      def bytes(s: String): Array[Byte] =
        s.getBytes(StandardCharsets.UTF_8)

      def entries(index: JvmIndex): Either[String, Seq[JvmIndexEntry]] =
        index.lookup("foo", "21", os = Some(os), arch = Some(arch))

      test("v2") {
        val index = JvmIndex.fromBytes(
          bytes(
            s"""{
               |  "foo": {
               |    "21.0.1": {
               |      "url": "$tgzUrl",
               |      "checksums": {"sha256": "abcd", "md5": "ef01"}
               |    },
               |    "21.0.2": {
               |      "url": "$zipUrl",
               |      "checksums": {}
               |    },
               |    "21.0.3": {
               |      "url": "https://foo.com/jdk-21.0.3.pkg",
               |      "checksums": {"sha256": "2345"}
               |    },
               |    "21.0.4": {
               |      "url": "https://foo.com/jdk-21.0.4.tar.xz",
               |      "future-field": true
               |    }
               |  }
               |}
               |""".stripMargin
          ),
          os,
          arch
        ).fold(throw _, identity)
        val expected = Right(Seq(
          JvmIndexEntry(
            os,
            arch,
            "foo",
            "21.0.1",
            ArchiveType.Tgz,
            tgzUrl,
            Map("sha256" -> "abcd", "md5" -> "ef01")
          ),
          JvmIndexEntry(os, arch, "foo", "21.0.2", ArchiveType.Zip, zipUrl),
          // 21.0.3 is ignored: archive type can't be deduced from its URL
          JvmIndexEntry(
            os,
            arch,
            "foo",
            "21.0.4",
            ArchiveType.Txz,
            "https://foo.com/jdk-21.0.4.tar.xz"
          )
        ))
        assert(entries(index) == expected)
        assert(index.available(Some(os), Some(arch)).map(_.keySet) == Right(Set("foo")))
      }

      test("v1") {
        val index = JvmIndex.fromBytes(
          bytes(
            s"""{"foo": {"21.0.1": "tgz+$tgzUrl", "21.0.2": "zip+$zipUrl"}}"""
          ),
          os,
          arch
        ).fold(throw _, identity)
        val expected = Right(Seq(
          JvmIndexEntry(os, arch, "foo", "21.0.1", ArchiveType.Tgz, tgzUrl),
          JvmIndexEntry(os, arch, "foo", "21.0.2", ArchiveType.Zip, zipUrl)
        ))
        assert(entries(index) == expected)
      }

      test("single") {
        val index = JvmIndex.fromBytes(
          bytes(
            s"""{"$os": {"$arch": {"jdk@foo": {"1.21.0.1": "tgz+$tgzUrl"}}}}"""
          ),
          "other-os",
          "other-arch"
        ).fold(throw _, identity)
        val expected = Right(Seq(
          JvmIndexEntry(os, arch, "foo", "1.21.0.1", ArchiveType.Tgz, tgzUrl)
        ))
        assert(entries(index) == expected)
      }

      test("invalid") {
        val res = JvmIndex.fromBytes(bytes("""{"foo": ["bar"]}"""), os, arch)
        assert(res.isLeft)
      }
    }

  }

}
