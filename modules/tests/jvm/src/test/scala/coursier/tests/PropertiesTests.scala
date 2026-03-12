package coursier.tests

import coursier.util.Properties
import utest._

object PropertiesTests extends TestSuite {

  val tests = Tests {

    test("version") {
      assert(Properties.version.nonEmpty)
    }

    test("commitHash") {
      assert(Properties.commitHash.nonEmpty)
    }
  }

}
