package coursier

import java.io.File

import coursier.core.Configuration
import coursier.ivy.IvyRepository
import utest._

import scala.async.Async.{async, await}

object FetchTests extends TestSuite {

  import TestHelpers.{ec, cache, validateArtifacts}

  val tests = Tests {

    'artifactTypes - {
      'default - async {

        val (res, artifacts) = await {
          Fetch()
            .noMirrors
            .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
            .withCache(cache)
            .futureResult()
        }

        await(validateArtifacts(res, artifacts.map(_._1)))
      }

      'sources - async {

        val classifiers = Set(Classifier.sources)
        val (res, artifacts) = await {
          Fetch()
            .noMirrors
            .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
            .withCache(cache)
            .withClassifiers(classifiers)
            .futureResult()
        }

        await(validateArtifacts(res, artifacts.map(_._1), classifiers = classifiers))
      }

      'mainAndSources - async {

        val classifiers = Set(Classifier.sources)
        val mainArtifacts = true
        val (res, artifacts) = await {
          Fetch()
            .noMirrors
            .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
            .withCache(cache)
            .withClassifiers(classifiers)
            .withMainArtifacts(mainArtifacts)
            .futureResult()
        }

        await(validateArtifacts(res, artifacts.map(_._1), classifiers = classifiers, mainArtifacts = mainArtifacts))
      }

      'javadoc - async {

        val classifiers = Set(Classifier.javadoc)
        val (res, artifacts) = await {
          Fetch()
            .noMirrors
            .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
            .withCache(cache)
            .withClassifiers(classifiers)
            .futureResult()
        }

        await(validateArtifacts(res, artifacts.map(_._1), classifiers = classifiers))
      }

      'mainAndJavadoc - async {

        val classifiers = Set(Classifier.javadoc)
        val mainArtifacts = true
        val (res, artifacts) = await {
          Fetch()
            .noMirrors
            .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
            .withCache(cache)
            .withClassifiers(classifiers)
            .withMainArtifacts(mainArtifacts)
            .futureResult()
        }

        await(validateArtifacts(res, artifacts.map(_._1), classifiers = classifiers, mainArtifacts = mainArtifacts))
      }

      'sourcesAndJavadoc - async {

        val classifiers = Set(Classifier.javadoc, Classifier.sources)
        val (res, artifacts) = await {
          Fetch()
            .noMirrors
            .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
            .withCache(cache)
            .withClassifiers(classifiers)
            .futureResult()
        }

        await(validateArtifacts(res, artifacts.map(_._1), classifiers = classifiers))
      }
    }

    'testScope - {

      val base = new File("modules/tests/handmade-metadata/data").getAbsoluteFile

      val m2Local = new File(base, "http/abc.com").toURI.toASCIIString
      val ivy2Local = new File(base, "http/ivy.abc.com").toURI.toASCIIString

      val m2Repo = MavenRepository(m2Local)
      val ivy2Repo = IvyRepository.parse(ivy2Local + "/[defaultPattern]").right.get

      val fetch0 = Fetch()
        .noMirrors
        .withCache(cache)
        .withRepositories(Seq(Repositories.central))

      'm2Local - async {
        val (res, artifacts) = await {
          fetch0
            .addRepositories(m2Repo)
            .addDependencies(
              dep"com.thoughtworks:top_2.12:0.1.0-SNAPSHOT"
                .copy(configuration = Configuration.test)
            )
            .futureResult()
        }

        val urls = artifacts.map(_._1.url).toSet

        assert(urls.exists(_.endsWith("/common_2.12-0.1.0-SNAPSHOT.jar")))
        assert(urls.exists(_.endsWith("/top_2.12-0.1.0-SNAPSHOT.jar")))
        assert(urls.exists(_.endsWith("/top_2.12-0.1.0-SNAPSHOT-tests.jar")))
        assert(urls.contains("https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.9.0/jackson-annotations-2.9.0.jar"))

        // those ones aren't here, unlike in the Ivy case
        // see below for more details
        assert(!urls.exists(_.endsWith("/common_2.12-0.1.0-SNAPSHOT-tests.jar")))
        assert(!urls.contains("https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar"))

        await(validateArtifacts(res, artifacts.map(_._1), extraKeyPart = "_m2Local"))
      }

      'ivy2Local - async {
        val (res, artifacts) = await {
          fetch0
            .addRepositories(ivy2Repo)
            .addDependencies(
              dep"com.thoughtworks:top_2.12:0.1.0-SNAPSHOT"
                .copy(configuration = Configuration.test)
            )
            .futureResult()
        }

        val urls = artifacts.map(_._1.url).toSet

        assert(urls.exists(_.endsWith("/common_2.12.jar")))
        assert(urls.exists(_.endsWith("/top_2.12.jar")))
        assert(urls.exists(_.endsWith("/top_2.12-tests.jar")))
        assert(urls.contains("https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.9.0/jackson-annotations-2.9.0.jar"))

        // those ones are here, unlike in the Maven case
        // brought via a test->test dependency of module top on module common, that can't be represented in a POM
        assert(urls.exists(_.endsWith("/common_2.12-tests.jar")))
        // brought via a dependency on the test scope of common, via the same test->test dependency
        assert(urls.contains("https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar"))

        await(validateArtifacts(res, artifacts.map(_._1), extraKeyPart = "_ivy2Local"))
      }
    }

  }

}
