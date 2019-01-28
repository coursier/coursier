package coursier.test

import coursier.cache.LocalRepositories
import coursier.core.{Classifier, Type}
import coursier.{Dependency, moduleString}
import coursier.test.compatibility._

import scala.async.Async.{async, await}
import utest._

object IvyLocalTests extends TestSuite {

  private val runner = new TestRunner

  def localVersion = "0.1.2-publish-local"

  val tests = TestSuite{
    'coursier {
      val module = mod"io.get-coursier:coursier-core_2.12"

      val extraRepos = Seq(LocalRepositories.ivy2Local)

      // Assuming this module (and the sub-projects it depends on) is published locally
      'resolution - runner.resolutionCheck(
        module, localVersion,
        extraRepos
      )

      'uniqueArtifacts - async {

        val res = await(runner.resolve(
          Seq(Dependency(mod"io.get-coursier:coursier-cli_2.12", localVersion, transitive = false)),
          extraRepos = extraRepos
        ))

        val artifacts = res.dependencyArtifacts(classifiers = Some(Seq(Classifier("standalone"))))
          .filter(t => t._2.`type` == Type.jar && !t._3.optional)
          .map(_._3)
          .map(_.url)
          .groupBy(s => s)

        assert(artifacts.nonEmpty)
        assert(artifacts.forall(_._2.length == 1))
      }


      'javadocSources - async {
        val res = await(runner.resolve(
          Seq(Dependency(module, localVersion)),
          extraRepos = extraRepos
        ))

        val artifacts = res.dependencyArtifacts().filter(_._2.`type` == Type.jar).map(_._3.url)
        val anyJavadoc = artifacts.exists(_.contains("-javadoc"))
        val anySources = artifacts.exists(_.contains("-sources"))

        assert(!anyJavadoc)
        assert(!anySources)
      }
    }
  }

}
