package coursier.core

import utest._

object VersionsTests extends TestSuite {

  val tests = Tests {

    test("latest_stable") {
      test("should ignore alpha versions") {
        val v =
          Versions(
            coursier.version.Version("1.1-a1"),
            coursier.version.Version("1.1-alpha1"),
            List(
              coursier.version.Version("1.1-alpha1"),
              coursier.version.Version("1.0.2"),
              coursier.version.Version("1.0.1"),
              coursier.version.Version("1.0.0")
            ),
            None
          )
        val candidates = v.candidates0(Latest.Stable).toSeq
        val expected = Seq(
          coursier.version.Version("1.0.2"),
          coursier.version.Version("1.0.1"),
          coursier.version.Version("1.0.0")
        )
        assert(candidates == expected)
      }

      test("should ignore milestones") {
        val v = Versions(
          coursier.version.Version("1.1-M1"),
          coursier.version.Version("1.1-M1"),
          List(
            coursier.version.Version("1.1-M1"),
            coursier.version.Version("1.0.2"),
            coursier.version.Version("1.0.1"),
            coursier.version.Version("1.0.0")
          ),
          None
        )
        val candidates = v.candidates0(Latest.Stable).toSeq
        val expected = Seq(
          coursier.version.Version("1.0.2"),
          coursier.version.Version("1.0.1"),
          coursier.version.Version("1.0.0")
        )
        assert(candidates == expected)
      }
    }

  }

}
