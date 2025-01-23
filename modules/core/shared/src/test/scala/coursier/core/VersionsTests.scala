package coursier.core

import coursier.version.{Latest => Latest0, Version => Version0}
import utest._

object VersionsTests extends TestSuite {

  val tests = Tests {

    test("latest_stable") {
      test("should ignore alpha versions") {
        val v =
          Versions(
            Version0("1.1-a1"),
            Version0("1.1-alpha1"),
            List(
              Version0("1.1-alpha1"),
              Version0("1.0.2"),
              Version0("1.0.1"),
              Version0("1.0.0")
            ),
            None
          )
        val candidates = v.candidates(Latest0.Stable).toSeq
        val expected = Seq(
          Version0("1.0.2"),
          Version0("1.0.1"),
          Version0("1.0.0")
        )
        assert(candidates == expected)
      }

      test("should ignore milestones") {
        val v = Versions(
          Version0("1.1-M1"),
          Version0("1.1-M1"),
          List(
            Version0("1.1-M1"),
            Version0("1.0.2"),
            Version0("1.0.1"),
            Version0("1.0.0")
          ),
          None
        )
        val candidates = v.candidates(Latest0.Stable).toSeq
        val expected = Seq(
          Version0("1.0.2"),
          Version0("1.0.1"),
          Version0("1.0.0")
        )
        assert(candidates == expected)
      }
    }

  }

}
