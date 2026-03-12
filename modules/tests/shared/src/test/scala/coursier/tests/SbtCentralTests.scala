package coursier.tests

import utest._

import scala.async.Async.{async, await}
import coursier.Repositories
import coursier.maven.SbtMavenRepository
import coursier.tests.compatibility._
import coursier.util.StringInterpolators._

import scala.concurrent.Future

object SbtCentralTests extends CentralTests {

  override def central = SbtMavenRepository(Repositories.central)

  override def tests = super.tests ++ Tests {

    test("sbtPlugin") {
      test("versionRange") {
        val mod = mod"org.ensime:sbt-ensime;scalaVersion=2.10;sbtVersion=0.13"
        val ver = "1.12.+"
        runner.resolutionCheck(mod, ver)
      }

      test("diamond") {
        // sbt-plugin-example-diamond is a diamond graph of sbt plugins.
        // Diamond depends on left and right which both depend on bottom.
        //             sbt-plugin-example-diamond
        //                        / \
        // sbt-plugin-example-left   sbt-plugin-example-right
        //                        \ /
        //             sbt-plugin-example-bottom
        // Depending on the version of sbt-plugin-example-diamond, different patterns
        // are tested:
        // - Some plugins are only published to the deprecated Maven path, some to the new
        // - There may be some conflict resolution to perform on sbt-plugin-example-bottom,
        //   mixing old and new Maven paths.
        val diamond =
          mod"ch.epfl.scala:sbt-plugin-example-diamond;scalaVersion=2.12;sbtVersion=1.0"

        // only deprecated Maven paths
        test("0.1.0") {
          runner.resolutionCheck(diamond, "0.1.0")
        }

        // diamond and left on the new Maven path
        test("0.2.0") {
          runner.resolutionCheck(diamond, "0.2.0")
        }

        // conflict resolution on bottom between new and deprecated Maven paths
        test("0.3.0") {
          runner.resolutionCheck(diamond, "0.3.0")
        }

        // bottom on new Maven paht, but not righ
        test("0.4.0") {
          runner.resolutionCheck(diamond, "0.4.0")
        }

        // only new Maven paths with conflict resolution on bottom
        test("0.5.0") {
          runner.resolutionCheck(diamond, "0.5.0")
        }
      }
    }
  }

}
