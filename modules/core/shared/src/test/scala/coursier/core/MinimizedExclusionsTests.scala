package coursier.core

import coursier.core.MinimizedExclusions._
import utest._

object MinimizedExclusionsTests extends TestSuite {

  val tests = Tests {

    // I tried writing property-based tests here, but simple generators weren't catching
    // anything, and complex ones end up being targeted at one specific invalid shape, which we might
    // as well just do manual tests about

    test("manual checks") {
      test {
        val a = ExcludeAll
        val b = ExcludeSpecific(Set(Organization("foo"), Organization("thing")), Set(), Set())
        assert(b.subsetOf(a))
        assert(b.size() <= a.size())
      }
      test {
        val a = ExcludeSpecific(Set(Organization("foo")), Set(), Set())
        val b = ExcludeSpecific(
          Set(),
          Set(),
          Set(Organization("foo") -> ModuleName("a"), Organization("foo") -> ModuleName("b"))
        )
        assert(b.subsetOf(a))
        assert(b.size() <= a.size())
      }
    }
  }

}
