package coursier

import java.io.File

import coursier.core.{Configuration, Extension}
import coursier.ivy.IvyRepository
import utest._

import scala.async.Async.{async, await}

object FetchTests extends TestSuite {

  import TestHelpers.{ec, cache, cacheWithHandmadeMetadata, handmadeMetadataBase, validateArtifacts}

  val tests = Tests {

    'artifactTypes - {
      'default - async {

        val res = await {
          Fetch()
            .noMirrors
            .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
            .withCache(cache)
            .futureResult()
        }

        await(validateArtifacts(res.resolution, res.artifacts.map(_._1)))
      }

      'sources - async {

        val classifiers = Set(Classifier.sources)
        val res = await {
          Fetch()
            .noMirrors
            .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
            .withCache(cache)
            .withClassifiers(classifiers)
            .futureResult()
        }

        await(validateArtifacts(res.resolution, res.artifacts.map(_._1), classifiers = classifiers))
      }

      'mainAndSources - async {

        val classifiers = Set(Classifier.sources)
        val mainArtifacts = true
        val res = await {
          Fetch()
            .noMirrors
            .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
            .withCache(cache)
            .withClassifiers(classifiers)
            .withMainArtifacts(mainArtifacts)
            .futureResult()
        }

        await(validateArtifacts(res.resolution, res.artifacts.map(_._1), classifiers = classifiers, mainArtifacts = mainArtifacts))
      }

      'javadoc - async {

        val classifiers = Set(Classifier.javadoc)
        val res = await {
          Fetch()
            .noMirrors
            .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
            .withCache(cache)
            .withClassifiers(classifiers)
            .futureResult()
        }

        await(validateArtifacts(res.resolution, res.artifacts.map(_._1), classifiers = classifiers))
      }

      'mainAndJavadoc - async {

        val classifiers = Set(Classifier.javadoc)
        val mainArtifacts = true
        val res = await {
          Fetch()
            .noMirrors
            .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
            .withCache(cache)
            .withClassifiers(classifiers)
            .withMainArtifacts(mainArtifacts)
            .futureResult()
        }

        await(validateArtifacts(res.resolution, res.artifacts.map(_._1), classifiers = classifiers, mainArtifacts = mainArtifacts))
      }

      'sourcesAndJavadoc - async {

        val classifiers = Set(Classifier.javadoc, Classifier.sources)
        val res = await {
          Fetch()
            .noMirrors
            .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
            .withCache(cache)
            .withClassifiers(classifiers)
            .futureResult()
        }

        await(validateArtifacts(res.resolution, res.artifacts.map(_._1), classifiers = classifiers))
      }

      'exotic - {
        'orbit - async {
          // should be in the default artifact types
          //

          val res = await {
            Fetch()
              .noMirrors
              .addDependencies(dep"org.eclipse.jetty.orbit:javax.servlet:3.0.0.v201112011016")
              .withCache(cache)
              .futureResult()
          }

          val urls = res.artifacts.map(_._1.url)
          assert(urls.contains("https://repo1.maven.org/maven2/org/eclipse/jetty/orbit/javax.servlet/3.0.0.v201112011016/javax.servlet-3.0.0.v201112011016.jar"))

          await(validateArtifacts(res.resolution, res.artifacts.map(_._1)))
        }
      }
    }

    'testScope - {

      val base = new File("modules/tests/handmade-metadata/data").getAbsoluteFile

      val m2Local = new File(base, "http/abc.com").toURI.toASCIIString
      val ivy2Local = new File(base, "http/ivy.abc.com").toURI.toASCIIString

      val m2Repo = MavenRepository(m2Local)
      val ivy2Repo = IvyRepository.parse(ivy2Local + "/[defaultPattern]").toOption.get

      val fetch0 = Fetch()
        .noMirrors
        .withCache(cache)
        .withRepositories(Seq(Repositories.central))

      'm2Local - async {
        val res = await {
          fetch0
            .addRepositories(m2Repo)
            .addDependencies(
              dep"com.thoughtworks:top_2.12:0.1.0-SNAPSHOT"
                .withConfiguration(Configuration.test)
            )
            .futureResult()
        }

        val urls = res.artifacts.map(_._1.url).toSet

        assert(urls.exists(_.endsWith("/common_2.12-0.1.0-SNAPSHOT.jar")))
        assert(urls.exists(_.endsWith("/top_2.12-0.1.0-SNAPSHOT.jar")))
        assert(urls.exists(_.endsWith("/top_2.12-0.1.0-SNAPSHOT-tests.jar")))
        assert(urls.contains("https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.9.0/jackson-annotations-2.9.0.jar"))

        // those ones aren't here, unlike in the Ivy case
        // see below for more details
        assert(!urls.exists(_.endsWith("/common_2.12-0.1.0-SNAPSHOT-tests.jar")))
        assert(!urls.contains("https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar"))

        await(validateArtifacts(res.resolution, res.artifacts.map(_._1), extraKeyPart = "_m2Local"))
      }

      'ivy2Local - async {
        val res = await {
          fetch0
            .addRepositories(ivy2Repo)
            .addDependencies(
              dep"com.thoughtworks:top_2.12:0.1.0-SNAPSHOT"
                .withConfiguration(Configuration.test)
            )
            .futureResult()
        }

        val urls = res.artifacts.map(_._1.url).toSet

        assert(urls.exists(_.endsWith("/common_2.12.jar")))
        assert(urls.exists(_.endsWith("/top_2.12.jar")))
        assert(urls.exists(_.endsWith("/top_2.12-tests.jar")))
        assert(urls.contains("https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.9.0/jackson-annotations-2.9.0.jar"))

        // those ones are here, unlike in the Maven case
        // brought via a test->test dependency of module top on module common, that can't be represented in a POM
        assert(urls.exists(_.endsWith("/common_2.12-tests.jar")))
        // brought via a dependency on the test scope of common, via the same test->test dependency
        assert(urls.contains("https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar"))

        await(validateArtifacts(res.resolution, res.artifacts.map(_._1), extraKeyPart = "_ivy2Local"))
      }
    }

    'properties - {

      val fetch0 = Fetch()
        .noMirrors
        .withCache(cache)
        .withRepositories(Seq(
          Repositories.central,
          mvn"http://repository.splicemachine.com/nexus/content/groups/public",
          mvn"http://repository.mapr.com/maven"
        ))
        .addDependencies(
          dep"com.splicemachine:splice_spark:2.8.0.1915-SNAPSHOT"
        )
        .mapResolutionParams(
          _.addForceVersion(
            mod"org.apache.hadoop:hadoop-common" -> "2.7.3"
          )
        )

      val prop = "env" -> "mapr6.1.0"

      // would be nice to have tests with different results, whether the properties
      // are forced or not

      * - async {
        val res = await {
          fetch0
            .mapResolutionParams(_.addForcedProperties(prop))
            .futureResult()
        }

        await(validateArtifacts(res.resolution, res.artifacts.map(_._1)))
      }

      * - async {
        val res = await {
          fetch0
            .mapResolutionParams(_.addProperties(prop))
            .futureResult()
        }

        await(validateArtifacts(res.resolution, res.artifacts.map(_._1)))
      }
    }

    'publications - {
      'ivy - async {
        val artifactTypes = Seq(Type("info"))

        val res = await {
          Fetch()
            .noMirrors
            .withRepositories(Seq(
              Repositories.central,
              IvyRepository.parse("http://ivy.abc.com/[defaultPattern]").toOption.get
            ))
            .addDependencies(dep"test:a_2.12:1.0.0")
            .addArtifactTypes(artifactTypes: _*)
            .withCache(cacheWithHandmadeMetadata)
            .futureResult()
        }

        assert(res.artifacts.nonEmpty)
        assert(res.detailedArtifacts.count(_._2.ext == Extension("csv")) == 1)

        await(validateArtifacts(res.resolution, res.artifacts.map(_._1), artifactTypes = artifactTypes.toSet))
      }
    }

    'subset - async {

      val res = await {
        Fetch()
          .noMirrors
          .addDependencies(dep"sh.almond:scala-kernel_2.12.8:0.7.0")
          .withCache(cache)
          .addRepositories(Repositories.jitpack)
          .futureResult()
      }

      await(validateArtifacts(res.resolution, res.artifacts.map(_._1)))

      val subsetRes = res.resolution.subset(Seq(dep"sh.almond:scala-kernel-api_2.12.8:_"))

      val subsetArtifacts = await {
        Artifacts()
          .withResolution(subsetRes)
          .future()
      }

      await(validateArtifacts(subsetRes, subsetArtifacts.map(_._1)))

      val subsetSourcesArtifacts = await {
        Artifacts()
          .withResolution(subsetRes)
          .withClassifiers(Set(Classifier.sources))
          .future()
      }

      await(validateArtifacts(subsetRes, subsetSourcesArtifacts.map(_._1), classifiers = Set(Classifier.sources)))
    }

  }

}
