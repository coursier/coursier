package coursier.cli

import coursier.cli.util.JsonReport
import coursier.tests.TestHelpers
import utest._

object DummyJsonReportTests extends TestSuite {

  def jsonLines(jsonStr: String): Seq[String] =
    ujson.read(jsonStr)
      .render(indent = 2)
      .linesIterator
      .toVector

  val tests = Tests {
    test("empty JsonReport should be empty") {
      TestHelpers.validateResult(s"${TestHelpers.testDataDir}/dummy-reports/empty.json") {
        jsonLines {
          JsonReport[String](Vector.empty, Map())(
            children = _ => Vector.empty,
            reconciledVersionStr = _ => "",
            requestedVersionStr = _ => "",
            getFile = _ => Option(""),
            exclusions = _ => Set.empty
          )
        }
      }
    }

    test("JsonReport containing two deps should not be empty") {
      val children = Map("a" -> Seq("b"), "b" -> Seq())

      TestHelpers.validateResult(s"${TestHelpers.testDataDir}/dummy-reports/two-deps.json") {
        jsonLines {
          JsonReport[String](
            roots = Vector("a", "b"),
            conflictResolutionForRoots = Map()
          )(
            children = children(_).toVector,
            reconciledVersionStr = s => s"$s:reconciled",
            requestedVersionStr = s => s"$s:requested",
            getFile = _ => Option(""),
            exclusions = _ => Set.empty
          )
        }
      }
    }

    test(
      "JsonReport containing two deps should be sorted alphabetically regardless of input order"
    ) {
      val children = Map("a" -> Seq("b"), "b" -> Seq())

      TestHelpers.validateResult(s"${TestHelpers.testDataDir}/dummy-reports/two-deps-order.json") {
        jsonLines {
          JsonReport[String](
            roots = Vector("b", "a"),
            conflictResolutionForRoots = Map()
          )(
            children = children(_).toVector,
            reconciledVersionStr = s => s"$s:reconciled",
            requestedVersionStr = s => s"$s:requested",
            getFile = _ => Option(""),
            exclusions = _ => Set.empty
          )
        }
      }
    }

    test("JsonReport should prevent walking a tree in which a dependency depends on itself") {
      val children = Map("a" -> Vector("a", "b"), "b" -> Vector.empty)

      TestHelpers.validateResult(s"${TestHelpers.testDataDir}/dummy-reports/self-dependency.json") {
        jsonLines {
          JsonReport[String](
            roots = Vector("a", "b"),
            conflictResolutionForRoots = Map.empty
          )(
            children = children(_),
            reconciledVersionStr = s => s"$s:reconciled",
            requestedVersionStr = s => s"$s:requested",
            getFile = _ => Option(""),
            exclusions = _ => Set.empty
          )
        }
      }
    }

    test("JsonReport should prevent walking a tree with cycles") {
      val children =
        Map("a" -> Vector("b"), "b" -> Vector("c"), "c" -> Vector("a", "d"), "d" -> Vector.empty)

      TestHelpers.validateResult(s"${TestHelpers.testDataDir}/dummy-reports/cycle.json") {
        jsonLines {
          JsonReport[String](
            roots = Vector("a", "b", "c"),
            conflictResolutionForRoots = Map.empty
          )(
            children = children(_),
            reconciledVersionStr = s => s"$s:reconciled",
            requestedVersionStr = s => s"$s:requested",
            getFile = _ => Option(""),
            exclusions = _ => Set.empty
          )
        }
      }
    }
  }
}
