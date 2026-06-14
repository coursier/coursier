package coursier.tests

import coursier.version.{Version, VersionConstraint, VersionInterval, VersionParse}
import utest._

object VersionConstraintTests extends TestSuite {

  val tests = Tests {
    test("parse") {
      test("empty") {
        val c0 = VersionParse.versionConstraint("")
        assert(c0 == VersionConstraint.empty)
      }
      test("basicVersion") {
        val c0 = VersionParse.versionConstraint("1.2")
        assert(c0 == VersionConstraint.from(VersionInterval.zero, Some(Version("1.2")), None))
      }
      test("basicVersionInterval") {
        val c0               = VersionParse.versionConstraint("(,1.2]")
        val expectedInterval = VersionInterval(
          None,
          Some(Version("1.2")),
          false,
          true
        )
        assert(c0 == VersionConstraint.from(expectedInterval, None, None))
      }
      test("latestSubRevision") {
        val c0               = VersionParse.versionConstraint("1.2.3-+")
        val expectedInterval = VersionInterval(
          Some(Version("1.2.3")),
          Some(Version("1.2.3-max")),
          true,
          true
        )
        assert(c0.generateString == VersionConstraint.from(
          expectedInterval,
          None,
          None
        ).generateString)
      }
      test("latestSubRevisionWithLiteral") {
        val c0               = VersionParse.versionConstraint("1.2.3-rc-+")
        val expectedInterval = VersionInterval(
          Some(Version("1.2.3-rc")),
          Some(Version("1.2.3-rc-max")),
          true,
          true
        )
        assert(c0.generateString == VersionConstraint.from(
          expectedInterval,
          None,
          None
        ).generateString)
      }
      test("latestSubRevisionWithZero") {
        val c0               = VersionParse.versionConstraint("1.0.+")
        val expectedInterval = VersionInterval(
          Some(Version("1.0")),
          Some(Version("1.0.max")),
          true,
          true
        )
        assert(c0.generateString == VersionConstraint.from(
          expectedInterval,
          None,
          None
        ).generateString)
      }
    }

    test("interval") {
      test("checkZero") {
        val v103 = Version("1.0.3")
        val v107 = Version("1.0.7")
        val v112 = Version("1.1.2")
        val c0   = VersionInterval(Some(Version("1.0")), Some(Version("1.0.max")), true, true)
        assert(c0.contains(v103))
        assert(c0.contains(v107))
        assert(!c0.contains(v112))
      }
      test("subRevision") {
        val v  = Version("1.2.3-rc")
        val c0 = VersionInterval(Some(v), Some(Version("1.2.3-rc-max")), true, true)
        assert(c0.contains(v))
        assert(c0.contains(Version("1.2.3-rc-500")))
        assert(!c0.contains(Version("1.2.3-final")))
      }
    }

    test("repr") {
      test("empty") {
        val s0 = VersionConstraint.empty.asString
        assert(s0 == "")
      }
      test("preferred") {
        val s0 = VersionConstraint.from(VersionInterval.zero, Some(Version("2.1")), None).asString
        assert(s0 == "2.1")
      }
      test("interval") {
        val s0 =
          VersionConstraint.from(
            VersionInterval(None, Some(Version("2.1")), false, true),
            None,
            None
          ).asString
        assert(s0 == "(,2.1]")
      }
    }

    test("merge") {
      test {
        val s0 = VersionConstraint.merge(
          VersionParse.versionConstraint("[1.0,3.2]"),
          VersionParse.versionConstraint("[3.0,4.0)")
        ).get.asString
        assert(s0.contains("[3.0,3.2]"))
      }

      test {
        val c0 = VersionConstraint.merge(
          VersionParse.versionConstraint("[1.0,2.0)"),
          VersionParse.versionConstraint("[3.0,4.0)")
        )
        assert(c0.isEmpty)
      }

      test {
        val c0 = VersionConstraint.merge(
          VersionParse.versionConstraint("[1.0,2.0)"),
          VersionParse.versionConstraint("[3.0,4.0)"),
          VersionParse.versionConstraint("2.8")
        )
        assert(c0.isEmpty)
      }
    }

    test("relaxedMerge") {
      test {
        val s0 = VersionConstraint.relaxedMerge(
          VersionParse.versionConstraint("[1.0,2.0)"),
          VersionParse.versionConstraint("[3.0,4.0)")
        ).asString
        assert(s0 == "[3.0,4.0)")
      }

      test {
        val s0 = VersionConstraint.relaxedMerge(
          VersionParse.versionConstraint("[1.0,2.0)"),
          VersionParse.versionConstraint("[3.0,4.0)"),
          VersionParse.versionConstraint("2.8")
        ).asString
        assert(s0 == "[3.0,4.0)")
      }
    }
  }

}
