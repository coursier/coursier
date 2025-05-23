package coursier.tests

import java.io.File

import coursier.LocalRepositories
import coursier.core.{Classifier, Dependency, Type}
import coursier.ivy.{IvyRepository, Pattern}
import coursier.testcache.TestCache
import coursier.tests.compatibility._
import coursier.util.StringInterpolators._
import utest._

import scala.async.Async.{async, await}
import coursier.version.VersionConstraint

object IvyLocalTests extends TestSuite {

  private val runner = new TestRunner

  lazy val localVersion = VersionConstraint("0.1.2-publish-local")

  val tests = TestSuite {
    test("coursier") {
      val module = mod"io.get-coursier:coursier-core_2.12"

      val mockIvy2Local = IvyRepository.fromPattern(
        TestCache.dataDir.resolve("ivy-local").toUri.toASCIIString +: Pattern.default,
        dropInfoAttributes = true
      )
      val extraRepos = Seq(mockIvy2Local)

      // Assuming this module (and the sub-projects it depends on) is published locally
      test("resolution") {
        runner.resolutionCheck0(
          module,
          localVersion,
          extraRepos
        )
      }

      test("uniqueArtifacts") {
        async {

          val res = await(runner.resolve(
            Seq(
              Dependency(mod"io.get-coursier:coursier-cli_2.12", localVersion).withTransitive(false)
            ),
            extraRepos = extraRepos
          ))

          val artifacts = res.dependencyArtifacts0()
            .filter(t => t._2.exists(_.`type` == Type.jar) && !t._3.optional)
            .map(_._3)
            .map(_.url)
            .groupBy(s => s)

          assert(artifacts.nonEmpty)
          assert(artifacts.forall(_._2.length == 1))
        }
      }

      test("javadocSources") {
        async {
          val res = await(runner.resolve(
            Seq(Dependency(module, localVersion)),
            extraRepos = extraRepos
          ))

          val artifacts =
            res.dependencyArtifacts0().filter(_._2.exists(_.`type` == Type.jar)).map(_._3.url)
          val anyJavadoc = artifacts.exists(_.contains("-javadoc"))
          val anySources = artifacts.exists(_.contains("-sources"))

          assert(!anyJavadoc)
          assert(!anySources)
        }
      }
    }
  }

}
