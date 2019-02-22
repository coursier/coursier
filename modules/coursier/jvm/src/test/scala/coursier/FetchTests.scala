package coursier

import coursier.core.Classifier
import utest._

import scala.async.Async.{async, await}

object FetchTests extends TestSuite {

  import TestHelpers.{ec, cache, validateArtifacts}

  val tests = Tests {

    'artifactTypes - {
      'default - async {

        val (res, artifacts) = await {
          Fetch.fetchFuture(
            Seq(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8"),
            cache = cache
          )
        }

        await(validateArtifacts(res, artifacts.map(_._1)))
      }

      'sources - async {

        val classifiers = Set(Classifier.sources)
        val (res, artifacts) = await {
          Fetch.fetchFuture(
            Seq(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8"),
            cache = cache,
            classifiers = classifiers
          )
        }

        await(validateArtifacts(res, artifacts.map(_._1), classifiers = classifiers))
      }

      'mainAndSources - async {

        val classifiers = Set(Classifier.sources)
        val mainArtifacts = true
        val (res, artifacts) = await {
          Fetch.fetchFuture(
            Seq(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8"),
            cache = cache,
            classifiers = classifiers,
            mainArtifacts = mainArtifacts
          )
        }

        await(validateArtifacts(res, artifacts.map(_._1), classifiers = classifiers, mainArtifacts = mainArtifacts))
      }

      'javadoc - async {

        val classifiers = Set(Classifier.javadoc)
        val (res, artifacts) = await {
          Fetch.fetchFuture(
            Seq(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8"),
            cache = cache,
            classifiers = classifiers
          )
        }

        await(validateArtifacts(res, artifacts.map(_._1), classifiers = classifiers))
      }

      'mainAndJavadoc - async {

        val classifiers = Set(Classifier.javadoc)
        val mainArtifacts = true
        val (res, artifacts) = await {
          Fetch.fetchFuture(
            Seq(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8"),
            cache = cache,
            classifiers = classifiers,
            mainArtifacts = mainArtifacts
          )
        }

        await(validateArtifacts(res, artifacts.map(_._1), classifiers = classifiers, mainArtifacts = mainArtifacts))
      }

      'sourcesAndJavadoc - async {

        val classifiers = Set(Classifier.javadoc, Classifier.sources)
        val (res, artifacts) = await {
          Fetch.fetchFuture(
            Seq(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8"),
            cache = cache,
            classifiers = classifiers
          )
        }

        await(validateArtifacts(res, artifacts.map(_._1), classifiers = classifiers))
      }
    }

  }

}
