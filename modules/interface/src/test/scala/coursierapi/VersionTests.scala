package coursierapi

import utest._

object VersionTests extends TestSuite {

  val tests = Tests {
    test("simple") {
      test {
        assert(Version.compare("1.0.1", "1.2.3") < 0)
      }
      test {
        assert(Version.compare("1.2.3", "1.0.1") > 0)
      }
      test {
        assert(Version.compare("1.0.1", "1.0.1") == 0)
      }
    }
  }
}
