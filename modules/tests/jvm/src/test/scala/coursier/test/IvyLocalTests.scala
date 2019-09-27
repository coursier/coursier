package coursier.test

import java.io.File

import coursier.{Classifier, Dependency, LocalRepositories, Type, moduleString}
import coursier.ivy.{IvyRepository, Pattern}
import coursier.test.compatibility._
import utest._

import scala.async.Async.{async, await}

object IvyLocalTests extends TestSuite {

  private val runner = new TestRunner

  def localVersion = "0.1.2-publish-local"

  val tests = TestSuite{
    'coursier {
      val module = mod"io.get-coursier:coursier-core_2.12"

      val mockIvy2Local = IvyRepository.fromPattern(
        new File("modules/tests/metadata/ivy-local").getAbsoluteFile.toURI.toASCIIString +: Pattern.default,
        dropInfoAttributes = true
      )
      val extraRepos = Seq(mockIvy2Local)

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

        val artifacts = res.dependencyArtifacts()
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
