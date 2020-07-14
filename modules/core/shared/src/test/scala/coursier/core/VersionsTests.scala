package coursier.core

import utest._

object VersionsTests extends TestSuite {

  val tests = Tests {

    test("latest.stable") {
      test("should ignore alpha versions") {
        val v = Versions("1.1-a1", "1.1-alpha1", List("1.1-alpha1", "1.0.2", "1.0.1", "1.0.0"), None)
        val candidates = v.candidates(Latest.Stable).toSeq
        val expected = Seq("1.0.2", "1.0.1", "1.0.0")
        assert(candidates == expected)
      }

      test("should ignore milestones") {
        val v = Versions("1.1-M1", "1.1-M1", List("1.1-M1", "1.0.2", "1.0.1", "1.0.0"), None)
        val candidates = v.candidates(Latest.Stable).toSeq
        val expected = Seq("1.0.2", "1.0.1", "1.0.0")
        assert(candidates == expected)
      }
    }

  }

}
