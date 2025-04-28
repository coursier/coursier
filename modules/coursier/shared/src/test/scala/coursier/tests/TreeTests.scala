package coursier.tests

import coursier.Resolve
import coursier.util.StringInterpolators._
import utest._

import scala.async.Async.{async, await}
import coursier.error.ResolutionError
import coursier.util.Print.Colors

object TreeTests extends TestSuite {

  import TestHelpers.{ec, cache, handmadeMetadataBase, validateDependencies, versionOf}

  private val resolve = Resolve()
    .noMirrors
    .withCache(cache)

  def tests = Tests {
    test("root conflict") {
      async {
        val res = await {
          resolve
            .addDependencies(
              dep"com.softwaremill.sttp.client3:core_2.13:3.8.3",
              dep"org.scala-lang:scala-library:[2.13.8]"
            )
            .io
            .attempt
            .future()
        }
        res match {
          case Left(err: ResolutionError.ConflictingDependencies) =>
            val expectedTree =
              """Conflicting dependencies:
                |org.scala-lang:scala-library:2.13.8 or 2.13.9 or 2.13.10 or [2.13.8] wanted by
                |
                |  org.scala-lang:scala-library:[2.13.8] wants [2.13.8]
                |
                |  com.softwaremill.sttp.client3:core_2.13:3.8.3 wants 2.13.10
                |  └─ com.softwaremill.sttp.client3:core_2.13:3.8.3
                |
                |  com.softwaremill.sttp.model:core_2.13:1.5.2 wants 2.13.8
                |  ├─ com.softwaremill.sttp.client3:core_2.13:3.8.3
                |  │  └─ com.softwaremill.sttp.client3:core_2.13:3.8.3
                |  └─ com.softwaremill.sttp.shared:ws_2.13:1.3.10
                |     └─ com.softwaremill.sttp.client3:core_2.13:3.8.3
                |        └─ com.softwaremill.sttp.client3:core_2.13:3.8.3
                |
                |  com.softwaremill.sttp.shared:core_2.13:1.3.10 wants 2.13.9
                |  ├─ com.softwaremill.sttp.client3:core_2.13:3.8.3
                |  │  └─ com.softwaremill.sttp.client3:core_2.13:3.8.3
                |  └─ com.softwaremill.sttp.shared:ws_2.13:1.3.10
                |     └─ com.softwaremill.sttp.client3:core_2.13:3.8.3
                |        └─ com.softwaremill.sttp.client3:core_2.13:3.8.3
                |
                |  com.softwaremill.sttp.shared:ws_2.13:1.3.10 wants 2.13.9
                |  └─ com.softwaremill.sttp.client3:core_2.13:3.8.3
                |     └─ com.softwaremill.sttp.client3:core_2.13:3.8.3
                |
                |""".stripMargin
            val tree =
              ResolutionError.conflictingDependenciesErrorMessage(err.resolution, Colors.get(false))

            assert(expectedTree == tree.replace("\r\n", "\n"))
          case Left(other) =>
            throw new Exception(other)
          case Right(_) =>
            sys.error("Expected resolution to fail with a conflict")
        }
      }
    }
  }

}
