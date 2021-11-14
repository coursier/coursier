package coursier.parse

import utest._

object JavaOrScalaModuleTests extends TestSuite {

  val tests = Tests {

    test("scalaBinaryVersion") {
      import JavaOrScalaModule.scalaBinaryVersion

      /* Shamelessly copied from sbt's librarymanagement test cases at
       * https://github.com/sbt/librarymanagement/blob/f4cd994ab462031548c84479dcaa26c69e4509dd/core/src/test/scala/sbt/librarymanagement/CrossVersionTest.scala#L189-L260
       */

      // The 2.9.x scheme is not supported by coursier
      // assert(scalaBinaryVersion("2.9.2") == "2.9.2")

      assert(scalaBinaryVersion("2.10.0-M1") == "2.10.0-M1")
      assert(scalaBinaryVersion("2.10.0-RC1") == "2.10.0-RC1")
      assert(scalaBinaryVersion("2.10.0") == "2.10")
      assert(scalaBinaryVersion("2.10.1-M1") == "2.10")
      assert(scalaBinaryVersion("2.10.1-RC1") == "2.10")
      assert(scalaBinaryVersion("2.10.1") == "2.10")
      assert(scalaBinaryVersion("3.0.0-M2") == "3.0.0-M2")
      assert(scalaBinaryVersion("3.0.0-M3-bin-SNAPSHOT") == "3.0.0-M3")
      assert(scalaBinaryVersion("3.0.0-M3-bin-20201215-cbe50b3-NIGHTLY") == "3.0.0-M3")
      assert(scalaBinaryVersion("3.0.0-M3.5-bin-20201215-cbe50b3-NIGHTLY") == "3.0.0-M3.5")
      assert(scalaBinaryVersion("3.0.0-RC1") == "3.0.0-RC1")
      assert(scalaBinaryVersion("3.0.0") == "3")
      assert(scalaBinaryVersion("3.1.0-M1") == "3")
      assert(scalaBinaryVersion("3.1.0-RC1-bin-SNAPSHOT") == "3")
      assert(scalaBinaryVersion("3.1.0-RC1") == "3")
      assert(scalaBinaryVersion("3.1.0") == "3")
      assert(scalaBinaryVersion("3.0.1-RC1") == "3")
      assert(scalaBinaryVersion("3.0.1-M1") == "3")
      assert(scalaBinaryVersion("3.0.1-RC1-bin-SNAPSHOT") == "3")
      assert(scalaBinaryVersion("3.0.1-bin-SNAPSHOT") == "3")
      assert(scalaBinaryVersion("3.0.1-SNAPSHOT") == "3")
    }
  }

}
