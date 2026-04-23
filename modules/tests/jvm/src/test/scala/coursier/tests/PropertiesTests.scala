package coursier.tests

import coursier.util.Properties
import utest._

object PropertiesTests extends TestSuite {

  val tests = Tests {

    /** Verifies the `version` scenario behaves as the user expects. */
    test("version") {
      assert(Properties.version.nonEmpty)
    }

    /** Verifies the `commitHash` scenario behaves as the user expects. */
    test("commitHash") {
      assert(Properties.commitHash.nonEmpty)
    }
  }

}
