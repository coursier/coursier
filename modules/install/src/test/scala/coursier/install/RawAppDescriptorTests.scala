package coursier.install

import utest._
import coursier.core.VersionInterval
import coursier.core.Version
import cats.data.Validated
import coursier.core.Parse

object RawAppDescriptorTests extends TestSuite {
  val it1 = VersionInterval(Some(Version("2.0.1")), Some(Version("2.1.0")), true, true)
  val it2 = VersionInterval(Some(Version("2.1.2")), Some(Version("2.3.0")), true, false)
  val it3 = VersionInterval(Some(Version("3.0.0")), None, true, true)
  val it4 = VersionInterval(Some(Version("2.2.0")), Some(Version("3.2.0")), false, false)

  val vo1 = VersionOverride(it1)
  val vo2 = VersionOverride(it2)
  val vo3 = VersionOverride(it3)
  val vo4 = VersionOverride(it4)

  val tests: Tests = Tests {
    test("validate disjoint version intervals") {
      val versionOverrides = Seq(vo1, vo2, vo3)
      val validated        = RawAppDescriptor.validateRanges(versionOverrides)
      assert(validated == Validated.validNel(versionOverrides))
    }

    test("invalidate overlapping version intervals") {
      val versionOverrides = Seq(vo1, vo2, vo4)
      val validated        = RawAppDescriptor.validateRanges(versionOverrides)
      assertMatch(validated) { case Validated.Invalid(_) => () }
    }
  }
}
