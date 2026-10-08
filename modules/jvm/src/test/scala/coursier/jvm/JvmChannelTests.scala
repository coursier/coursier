package coursier.jvm

import utest._

object JvmChannelTests extends TestSuite {

  val tests = Tests {
    test("architecture") {
      test("amd64") {
        assert(JvmChannel.architecture(Some("amd64")) == Right("amd64"))
        assert(JvmChannel.architecture(Some("x86_64")) == Right("amd64"))
      }
      test("x86") {
        // 32-bit JVMs report these, even when running on a 64-bit OS
        for (arch <- Seq("x86", "i386", "i486", "i586", "i686"))
          assert(JvmChannel.architecture(Some(arch)) == Right("x86"))
      }
      test("arm") {
        assert(JvmChannel.architecture(Some("aarch64")) == Right("arm64"))
        assert(JvmChannel.architecture(Some("arm")) == Right("arm"))
      }
      test("unrecognized") {
        assert(JvmChannel.architecture(Some("sparc")).isLeft)
        assert(JvmChannel.architecture(None).isLeft)
      }
    }

    test("default JDK name on x86") {
      assert(JvmCache.defaultJdkNameFor("windows", "x86") == "liberica")
      assert(JvmCache.defaultJdkNameFor("linux", "x86") == "liberica")
      assert(JvmCache.defaultJdkNameFor("windows", "amd64") == "temurin")
    }

    test("x86 lookup") {
      val index = JvmIndex(
        Map(
          "windows" -> Map(
            "x86" -> Map(
              "jdk@liberica" -> Map(
                "21.0.8" -> "zip+https://foo.com/liberica-21.0.8-windows-i586.zip"
              )
            )
          )
        )
      )
      val res = index.lookup("liberica", "21", Some("windows"), Some("x86"))
      assert(
        res.map(_.map(_.url)) == Right(Seq("https://foo.com/liberica-21.0.8-windows-i586.zip"))
      )
    }
  }
}
