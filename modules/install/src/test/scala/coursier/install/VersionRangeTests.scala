package coursier.install

import utest._
import coursier.core.Parse
import coursier.core.Version

/** This test class is a copy of
  * https://github.com/sbt/librarymanagement/blob/develop/core/src/test/scala/sbt/librarymanagement/SemanticSelectorSpec.scala
  * It shows that sbt semantic selectors can be expressed using coursier `VersionInterval` syntax.
  * With some minor differences: in sbt 1.2.3-beta-gamma > 1.2.3-beta, not in coursier. And some
  * limitations: coursier does not yet support the union of disjoint intervals It can be added later
  * if the need arises: `class VersionRange(intervals: Seq[VersionInterval])`.
  */
object VersionRangeTests extends TestSuite {
  val tests = Tests {
    test("<=1.2.3") {
      checkRange(
        "(,1.2.3]",
        Seq("1.2.3", "1.2-beta", "1.2.3-beta", "1.2", "1"),
        Seq("1.2.4-alpha", "1.2.4", "1.3", "1.3.0", "2")
      )
    }

    test("<=1.2") {
      checkRange(
        "(,1.2.max]",
        Seq(
          "1.2.345-beta",
          "1.2.3-beta",
          "1.2.3",
          "1.2.0-beta",
          "1.2.0",
          "1.2",
          "1"
        ),
        Seq("1.3.0", "1.3.0-alpha")
      )
    }

    test("<=1") {
      checkRange(
        "(,1.max]",
        Seq(
          "1.234.567-alpha",
          "1.234.567",
          "1.234",
          "1.0.0-alpha",
          "1.0.0",
          "1.0",
          "1"
        ),
        Seq("2.0.0", "2.0.0-alpha")
      )
    }

    test("<1.2.3") {
      checkRange(
        "(,1.2.3)",
        Seq("1.2.3-alpha", "1.2.2", "1.2", "1"),
        Seq("1.2.4-beta", "1.2.3", "1.3", "2")
      )
    }

    test("<1.2") {
      checkRange(
        "(,1.2)",
        Seq("1.2.0-alpha", "1.1.23", "1.1", "1"),
        Seq("1.3-beta", "1.2.0", "1.2", "2")
      )
    }

    test("<1") {
      checkRange(
        "(,1)",
        Seq("1.0.0-beta", "0.9.9-beta", "0.9.12", "0.8", "0"),
        Seq("1.0.1-beta", "1", "1.0", "1.0.0")
      )
    }

    test(">=1.2.3") {
      checkRange(
        "[1.2.3,)",
        Seq("1.2.4-beta", "1.2.3", "1.3", "2"),
        Seq("1.2.3-beta", "1.2.2", "1.2", "1")
      )
    }

    test(">=1.2") {
      checkRange(
        "[1.2,)",
        Seq("1.2.1-beta", "1.2.0", "1.2", "2"),
        Seq("1.2.0-beta", "1.1.23", "1.1", "1")
      )
    }

    test(">=1") {
      checkRange(
        "[1,)",
        Seq("1.0.1-beta", "1.0.0", "1.0", "1"),
        Seq("1.0.0-beta", "0.9.9", "0.1", "0")
      )
    }

    test(">1.2.3") {
      checkRange(
        "(1.2.3,)",
        Seq("1.2.4", "1.2.4-alpha", "1.3", "2"),
        Seq("1.2.3-alpha", "1.2.3", "1.2", "1")
      )
    }

    test(">1.2") {
      checkRange(
        "(1.2.max,)",
        Seq("1.3.0", "1.3.0-alpha", "1.3", "2"),
        Seq(
          "1.2.0-alpha",
          "1.2.9",
          "1.2",
          "1"
        )
      )
    }

    test(">1") {
      checkRange(
        "(1.max,)",
        Seq("2.0.0-alpha", "2.0.0", "2.0", "2"),
        Seq(
          "1.2.3-alpha",
          "1.2.3",
          "1.2",
          "1"
        )
      )
    }

    test("1.2.3") {
      checkRange(
        "[1.2.3]",
        Seq("1.2.3"),
        Seq("1.2.3-alpha", "1.2", "1.2.4")
      )
    }

    test("1.x") {
      checkRange(
        "[1,1.max]",
        Seq("1.2.3-alpha", "1.0.0", "1.0.1", "1.1.1"),
        Seq("1.0.0-alpha", "2.0.0-alpha", "2.0.0", "0.1.0")
      )
    }

    test("1.2.x") {
      checkRange(
        "[1.2, 1.2.max]",
        Seq("1.2.0", "1.2.3"),
        Seq("1.2.0-alpha", "1.2.0-beta", "1.3.0-beta", "1.3.0", "1.1.1")
      )
    }

    test("=1.2.3") {
      checkRange(
        "[1.2.3]",
        Seq("1.2.3"),
        Seq("1.2.3-alpha", "1.2", "1.2.4")
      )
    }

    test("=1.2") {
      checkRange(
        "[1.2.0, 1.2.max]",
        Seq("1.2.0", "1.2", "1.2.1", "1.2.4"),
        Seq("1.1.0", "1.3.0", "1.2.0-alpha", "1.3.0-alpha")
      )
    }

    test("=1") {
      checkRange(
        "[1.0.0, 1.max]",
        Seq("1.0.0", "1.0", "1.0.1", "1.2.3"),
        Seq("1.0.0-alpha", "2.0.0")
      )
    }

    // test("1.2.3 || 2.0.0") {
    //   checkRange(
    //     "[1.2.3],[2.0.O]",
    //     Seq("1.2.3","2.0.0"),
    //     Seq("1.2","2.0.1")
    //   )
    // }

    // test("<=1.2.3 || >=2.0.0 || 1.3.x") {
    //   checkRange(
    //     "(,1.2.3],[2.0.0,),[1.3.0,1.3.max]",
    //     Seq("1.0","1.2.3","2.0.0","2.0","1.3.0","1.3.3"),
    //     Seq("1.2.4","1.4.0")
    //   )
    // }

    test(">=1.2.3 <2.0.0") {
      checkRange(
        "[1.2.3,2.0.0)",
        Seq("1.2.3", "1.9.9"),
        Seq("1.2", "2.0.0")
      )
    }

    // test(">=1.2.3 <2.0.0 || >3.0.0 <=3.2.0") {
    //   checkRange(
    //     "[1.2.3,2.0.0),(3.0.0,3.2.0]",
    //     Seq("1.2.3","1.9.9","3.0.1","3.2.0"),
    //     Seq("1.2","2.0.0","3.0.0","3.2.1")
    //   )
    // }

    test("1.2.3 - 2.0.0") {
      checkRange(
        "[1.2.3,2.0.0]",
        Seq("1.2.3", "1.9.9", "2.0.0"),
        Seq("1.2", "2.0.1")
      )
    }

    test("1.2 - 2") {
      checkRange(
        "[1.2,2.max]",
        Seq(
          "1.2.0",
          "1.9.9",
          "2.0.0",
          "2.0.1"
        ),
        Seq("1.1", "3.0.0")
      )
    }

    test("1.2.3 - 2.0.0 1.5.0 - 2.4.0") {
      checkRange(
        "[1.5.0,2.0.0]",
        Seq("1.5.0", "1.9.9", "2.0.0"),
        Seq("1.2.3", "1.4", "2.0.1", "2.4.0")
      )
    }

    // test("1.2.3 - 2.0 || 2.4.0 - 3") {
    //   checkRange(
    //     "[1.2.3,2.0.max],[2.4.0,3.max]",
    //     Seq("1.2.3","1.5.0","2.0.0","2.4.0","2.9","3.0.0","2.0.1","3.0.1","3.1.0"),
    //     Seq("2.1","2.3.9","4.0.0")
    //   )
    // }

    test(">=1.x") {
      checkRange(
        "[1,)",
        Seq("1.0.0", "1.0", "1"),
        Seq("1.0.0-beta", "0.9.9", "0.1", "0")
      )
    }

    test(">=1.2.3-beta") {
      checkRange(
        "[1.2.3-beta,)",
        Seq(
          "1.3-alpha",
          "1.2.3",
          "1.2.3-beta",
          "1.2.3-beta-2",
          // "1.2.3-beta-gamma",
          "1.2.4",
          "1.3"
        ),
        Seq("1.2.3-alpha", "1.2.2")
      )
    }

    test(">=1.2.3-beta-2") {
      checkRange(
        "[1.2.3-beta-2,)",
        Seq(
          "1.3-alpha",
          "1.2.3",
          "1.2.3-beta-2",
          "1.2.3-beta-2-3",
          "1.2.3-beta-3",
          // "1.2.3-beta-gamma",
          "1.2.4",
          "1.3"
        ),
        Seq("1.2.3-alpha-3", "1.2.3-beta-1", "1.2.3-beta", "1.2.2")
      )
    }
  }

  def checkRange(interval: String, contains: Seq[String], notContain: Seq[String]) = {
    val vi = Parse.versionInterval(interval).get
    contains.map(Version.apply).foreach(v => assert(vi.contains(v)))
    notContain.map(Version.apply).foreach(v => assert(!vi.contains(v)))

  }
}
