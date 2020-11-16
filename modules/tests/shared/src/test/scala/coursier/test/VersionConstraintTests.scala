package coursier
package test

import coursier.core._
import utest._

object VersionConstraintTests extends TestSuite {

  val tests = Tests {
    test("parse") {
      test("empty") {
        val c0 = Parse.versionConstraint("")
        assert(c0 == VersionConstraint.all)
      }
      test("basicVersion") {
        val c0 = Parse.versionConstraint("1.2")
        assert(c0 == VersionConstraint.preferred(Version("1.2")))
      }
      test("basicVersionInterval") {
        val c0 = Parse.versionConstraint("(,1.2]")
        assert(c0 == VersionConstraint.interval(VersionInterval(None, Some(Version("1.2")), false, true)))
      }
      test("latestSubRevision") {
        val c0 = Parse.versionConstraint("1.2.3-+")
        assert(c0 == VersionConstraint.interval(VersionInterval(Some(Version("1.2.3")), Some(Version("1.2.3-max")), true, true)))
      }
      test("latestSubRevisionWithLiteral") {
        val c0 = Parse.versionConstraint("1.2.3-rc-+")
        assert(c0 == VersionConstraint.interval(VersionInterval(Some(Version("1.2.3-rc")), Some(Version("1.2.3-rc-max")), true, true)))
      }
      test("latestSubRevisionWithZero") {
        val c0 = Parse.versionConstraint("1.0.+")
        assert(c0 == VersionConstraint.interval(VersionInterval(Some(Version("1.0")), Some(Version("1.0.max")), true, true)))
      }
    }

    test("interval") {
      test("checkZero") {
        val v103 = Version("1.0.3")
        val v107 = Version("1.0.7")
        val v112 = Version("1.1.2")
        val c0 = VersionInterval(Some(Version("1.0")), Some(Version("1.0.max")), true, true)
        assert(c0.contains(v103))
        assert(c0.contains(v107))
        assert(!c0.contains(v112))
      }
      test("subRevision") {
        val v = Version("1.2.3-rc")
        val c0 = VersionInterval(Some(v),  Some(Version("1.2.3-rc-max")), true, true)
        assert(c0.contains(v))
        assert(c0.contains(Version("1.2.3-rc-500")))
        assert(!c0.contains(Version("1.2.3-final")))
      }
    }

    test("repr") {
      test("empty") {
        val s0 = VersionConstraint.all.repr
        assert(s0.contains(""))
      }
      test("preferred") {
        val s0 = VersionConstraint.preferred(Version("2.1")).repr
        assert(s0.contains("2.1"))
      }
      test("interval") {
        val s0 = VersionConstraint.interval(VersionInterval(None, Some(Version("2.1")), false, true)).repr
        assert(s0.contains("(,2.1]"))
      }
    }

    test("merge") {
      test {
        val s0 = VersionConstraint.merge(
          Parse.versionConstraint("[1.0,3.2]"),
          Parse.versionConstraint("[3.0,4.0)")).get.repr
        assert(s0.contains("[3.0,3.2]"))
      }

      test {
        val c0 = VersionConstraint.merge(
          Parse.versionConstraint("[1.0,2.0)"),
          Parse.versionConstraint("[3.0,4.0)"))
        assert(c0.isEmpty)
      }

      test {
        val c0 = VersionConstraint.merge(
          Parse.versionConstraint("[1.0,2.0)"),
          Parse.versionConstraint("[3.0,4.0)"),
          Parse.versionConstraint("2.8"))
        assert(c0.isEmpty)
      }
    }

    test("relaxedMerge") {
      test {
        val s0 = VersionConstraint.relaxedMerge(
          Parse.versionConstraint("[1.0,2.0)"),
          Parse.versionConstraint("[3.0,4.0)")).repr
        assert(s0 == Some("[3.0,4.0)"))
      }

      test {
        val s0 = VersionConstraint.relaxedMerge(
          Parse.versionConstraint("[1.0,2.0)"),
          Parse.versionConstraint("[3.0,4.0)"),
          Parse.versionConstraint("2.8")).preferred.head.repr
        assert(s0 == "2.8")
      }
    }
  }

}
