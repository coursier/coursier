package coursier.jvm

import utest._

object JvmIndexTests extends TestSuite {

  val tests = Tests {

    "version in range" - {
      val os = "the-os"
      val arch = "the-arch"
      val prefix = "prefix@"
      val index = JvmIndex(
        Map(os -> Map(arch -> Map(
          s"${prefix}foo" -> Map(
            "18.1" -> "zip+https://foo.com/jdk-18.1.zip",
            "19.1.2" -> "zip+https://foo.com/jdk-19.1.2.zip",
            "19.1.3" -> "zip+https://foo.com/jdk-19.1.3.zip",
            "19.1.4" -> "zip+https://foo.com/jdk-19.1.4.zip",
            "20.1" -> "zip+https://foo.com/jdk-20.1.zip",
          )
        )))
      )

      val entry194 = JvmIndexEntry(os, arch, "foo", "19.1.4", ArchiveType.Zip, "https://foo.com/jdk-19.1.4.zip")

      * - {
        val res = index.lookup("foo", "19.1", os = Some(os), arch = Some(arch), jdkNamePrefix = Some(prefix))
        val expected = Right(entry194)
        assert(res == expected)
      }

      * - {
        val res = index.lookup("foo", "19.1+", os = Some(os), arch = Some(arch), jdkNamePrefix = Some(prefix))
        val expected = Right(entry194)
        assert(res == expected)
      }

      * - {
        val res = index.lookup("foo", "19.1.4", os = Some(os), arch = Some(arch), jdkNamePrefix = Some(prefix))
        val expected = Right(entry194)
        assert(res == expected)
      }

      * - {
        val res = index.lookup("foo", "19.1.5", os = Some(os), arch = Some(arch), jdkNamePrefix = Some(prefix))
        assert(res.isLeft)
      }
    }

    test("1-dot prefix") {
      val os = "the-os"
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

      val open192 = JvmIndexEntry(os, arch, "openfoo", "1.19-2", ArchiveType.Zip, "https://openfoo.com/jdk-19.2.zip")
      val open202 = JvmIndexEntry(os, arch, "openfoo", "1.20-2", ArchiveType.Zip, "https://openfoo.com/jdk-20.2.zip")

      test("add prefix") {
        val res = index.lookup("openfoo", "19-2", os = Some(os), arch = Some(arch))
        val expected = Right(open192)
        assert(res == expected)
      }

      test("add prefix with interval") {
        val res = index.lookup("openfoo", "19", os = Some(os), arch = Some(arch))
        val expected = Right(open192)
        assert(res == expected)
      }

      test("accept 1-dot nonetheless") {
        val res = index.lookup("openfoo", "1.19-2", os = Some(os), arch = Some(arch))
        val expected = Right(open192)
        assert(res == expected)
      }

      test("accept 1-dot nonetheless with interval") {
        val res = index.lookup("openfoo", "1.19", os = Some(os), arch = Some(arch))
        val expected = Right(open192)
        assert(res == expected)
      }

      test("accept just 1") {
        val res = index.lookup("openfoo", "1", os = Some(os), arch = Some(arch))
        val expected = Right(open202)
        assert(res == expected)
      }

      test("accept 1 plus") {
        val res = index.lookup("openfoo", "1+", os = Some(os), arch = Some(arch))
        val expected = Right(open202)
        assert(res == expected)
      }
    }

  }

}
