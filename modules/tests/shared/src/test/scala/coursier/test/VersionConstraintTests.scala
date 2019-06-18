package coursier
package test

import coursier.core._
import utest._

object VersionConstraintTests extends TestSuite {

  val tests = Tests {
    'parse{
      'empty{
        val c0 = Parse.versionConstraint("")
        assert(c0 == VersionConstraint.all)
      }
      'basicVersion{
        val c0 = Parse.versionConstraint("1.2")
        assert(c0 == VersionConstraint.preferred(Version("1.2")))
      }
      'basicVersionInterval{
        val c0 = Parse.versionConstraint("(,1.2]")
        assert(c0 == VersionConstraint.interval(VersionInterval(None, Some(Version("1.2")), false, true)))
      }
      'latestSubRevision{
        val c0 = Parse.versionConstraint("1.2.3-rc+")
        assert(c0 == VersionConstraint.interval(VersionInterval(Some(Version("1.2.3-rc")), Some(Version("1.2.3-rc-max")), true, false)))
      }
    }

    'interval{
      'same{
        val v = Version("1.2.3-rc")
        val c0 = VersionInterval(Some(v),  Some(Version("1.2.3-rc-max")), true, false)
        assert(c0.contains(v))
        assert(c0.contains(Version("1.2.3-rc-500")))
        assert(!c0.contains(Version("1.2.3-final")))
      }
    }

    'repr{
      'empty{
        val s0 = VersionConstraint.all.repr
        assert(s0 == Some(""))
      }
      'preferred{
        val s0 = VersionConstraint.preferred(Version("2.1")).repr
        assert(s0 == Some("2.1"))
      }
      'interval{
        val s0 = VersionConstraint.interval(VersionInterval(None, Some(Version("2.1")), false, true)).repr
        assert(s0 == Some("(,2.1]"))
      }
    }
  }

}
