package coursier.test

import coursier.core.{Classifier, Type}
import coursier.{Cache, Dependency, Module, moduleNameString, organizationString}
import coursier.test.compatibility._

import scala.async.Async.{async, await}
import utest._

object IvyLocalTests extends TestSuite {

  private val runner = new TestRunner

  val tests = TestSuite{
    'coursier {
      val module = Module(org"io.get-coursier", name"coursier-core_2.11")
      val version = coursier.util.Properties.version

      val extraRepos = Seq(Cache.ivy2Local)

      // Assuming this module (and the sub-projects it depends on) is published locally
      'resolution - runner.resolutionCheck(
        module, version,
        extraRepos
      )

      'uniqueArtifacts - async {

        val res = await(runner.resolve(
          Set(Dependency(Module(org"io.get-coursier", name"coursier-cli_2.12"), version, transitive = false)),
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
          Set(Dependency(module, version)),
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
