package coursier.tests

import java.io.File

import coursier.{Artifacts, Fetch, Repositories}
import coursier.core.{
  Activation,
  Classifier,
  Configuration,
  Dependency,
  Extension,
  Type,
  VariantSelector
}
import coursier.maven.{MavenRepository, MavenRepositoryLike}
import coursier.params.ResolutionParams
import coursier.ivy.IvyRepository
import coursier.util.StringInterpolators._
import coursier.util.{InMemoryRepository, Task}
import coursier.version.{Version, VersionConstraint}
import utest._

import scala.async.Async.{async, await}
import scala.concurrent.Future

object FetchTests extends TestSuite {

  import TestHelpers.{ec, cache, cacheWithHandmadeMetadata, handmadeMetadataBase, validateArtifacts}

  private val fetch = Fetch()
    .noMirrors
    .withCache(cache)

  def enableModules(fetch: Fetch[Task]): Fetch[Task] =
    fetch.withRepositories {
      fetch.repositories.map {
        case m: MavenRepositoryLike.WithModuleSupport =>
          m.withCheckModule(true)
        case other => other
      }
    }

  val tests = Tests {

    test("artifactTypes") {
      test("default") {
        async {

          val res = await {
            fetch
              .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
              .futureResult()
          }

          await(validateArtifacts(res.resolution, res.artifacts.map(_._1)))
        }
      }

      test("sources") {
        async {

          val classifiers = Set(Classifier.sources)
          val res = await {
            fetch
              .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
              .withClassifiers(classifiers)
              .futureResult()
          }

          await(validateArtifacts(
            res.resolution,
            res.artifacts.map(_._1),
            classifiers = classifiers
          ))
        }
      }

      test("mainAndSources") {
        async {

          val classifiers   = Set(Classifier.sources)
          val mainArtifacts = true
          val res = await {
            fetch
              .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
              .withClassifiers(classifiers)
              .withMainArtifacts(mainArtifacts)
              .futureResult()
          }

          await {
            validateArtifacts(
              res.resolution,
              res.artifacts.map(_._1),
              classifiers = classifiers,
              mainArtifacts = mainArtifacts
            )
          }
        }
      }

      test("javadoc") {
        async {

          val classifiers = Set(Classifier.javadoc)
          val res = await {
            fetch
              .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
              .withClassifiers(classifiers)
              .futureResult()
          }

          await(validateArtifacts(
            res.resolution,
            res.artifacts.map(_._1),
            classifiers = classifiers
          ))
        }
      }

      test("mainAndJavadoc") {
        async {

          val classifiers   = Set(Classifier.javadoc)
          val mainArtifacts = true
          val res = await {
            fetch
              .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
              .withClassifiers(classifiers)
              .withMainArtifacts(mainArtifacts)
              .futureResult()
          }

          await {
            validateArtifacts(
              res.resolution,
              res.artifacts.map(_._1),
              classifiers = classifiers,
              mainArtifacts = mainArtifacts
            )
          }
        }
      }

      test("sourcesAndJavadoc") {
        async {

          val classifiers = Set(Classifier.javadoc, Classifier.sources)
          val res = await {
            fetch
              .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
              .withClassifiers(classifiers)
              .futureResult()
          }

          await(validateArtifacts(
            res.resolution,
            res.artifacts.map(_._1),
            classifiers = classifiers
          ))
        }
      }

      test("exotic") {
        test("orbit") {
          async {
            // should be in the default artifact types
            //

            val res = await {
              fetch
                .addDependencies(dep"org.eclipse.jetty.orbit:javax.servlet:3.0.0.v201112011016")
                .futureResult()
            }

            val urls = res.artifacts.map(_._1.url)
            assert(urls.contains(
              "https://repo1.maven.org/maven2/org/eclipse/jetty/orbit/javax.servlet/3.0.0.v201112011016/javax.servlet-3.0.0.v201112011016.jar"
            ))

            await(validateArtifacts(res.resolution, res.artifacts.map(_._1)))
          }
        }

        test("klib") {
          async {
            // should be in the default artifact types

            val res = await {
              fetch
                .addDependencies(dep"org.jetbrains.kotlinx:kotlinx-html-js:0.11.0")
                .futureResult()
            }

            val urls = res.artifacts.map(_._1.url)
            assert(urls.contains(
              "https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-html-js/0.11.0/kotlinx-html-js-0.11.0.klib"
            ))
            assert(urls.contains(
              "https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-dom-api-compat/1.9.22/kotlin-dom-api-compat-1.9.22.klib"
            ))

            await(validateArtifacts(res.resolution, res.artifacts.map(_._1)))
          }
        }
      }
    }

    test("testScope") {

      val m2Local   = handmadeMetadataBase + "http/abc.com"
      val ivy2Local = handmadeMetadataBase + "http/ivy.abc.com"

      val m2Repo   = MavenRepository(m2Local)
      val ivy2Repo = IvyRepository.parse(ivy2Local + "/[defaultPattern]").toOption.get

      val fetch0 = fetch
        .withRepositories(Seq(Repositories.central))

      test("m2Local") {
        async {
          val res = await {
            fetch0
              .addRepositories(m2Repo)
              .addDependencies(
                dep"com.thoughtworks:top_2.12:0.1.0-SNAPSHOT"
                  .withVariantSelector(VariantSelector.ConfigurationBased(Configuration.test))
              )
              .futureResult()
          }

          val urls = res.artifacts.map(_._1.url).toSet

          assert(urls.exists(_.endsWith("/common_2.12-0.1.0-SNAPSHOT.jar")))
          assert(urls.exists(_.endsWith("/top_2.12-0.1.0-SNAPSHOT.jar")))
          assert(urls.exists(_.endsWith("/top_2.12-0.1.0-SNAPSHOT-tests.jar")))
          assert(urls.contains(
            "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.9.0/jackson-annotations-2.9.0.jar"
          ))

          // those ones aren't here, unlike in the Ivy case
          // see below for more details
          assert(!urls.exists(_.endsWith("/common_2.12-0.1.0-SNAPSHOT-tests.jar")))
          assert(!urls.contains("https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar"))

          await(validateArtifacts(
            res.resolution,
            res.artifacts.map(_._1),
            extraKeyPart = "_m2Local"
          ))
        }
      }

      test("ivy2Local") {
        async {
          val res = await {
            fetch0
              .addRepositories(ivy2Repo)
              .addDependencies(
                dep"com.thoughtworks:top_2.12:0.1.0-SNAPSHOT"
                  .withVariantSelector(VariantSelector.ConfigurationBased(Configuration.test))
              )
              .futureResult()
          }

          val urls = res.artifacts.map(_._1.url).toSet

          assert(urls.exists(_.endsWith("/common_2.12.jar")))
          assert(urls.exists(_.endsWith("/top_2.12.jar")))
          assert(urls.exists(_.endsWith("/top_2.12-tests.jar")))
          assert(urls.contains(
            "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.9.0/jackson-annotations-2.9.0.jar"
          ))

          // those ones are here, unlike in the Maven case
          // brought via a test->test dependency of module top on module common, that can't be represented in a POM
          assert(urls.exists(_.endsWith("/common_2.12-tests.jar")))
          // brought via a dependency on the test scope of common, via the same test->test dependency
          assert(urls.contains("https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar"))

          await {
            validateArtifacts(
              res.resolution,
              res.artifacts.map(_._1),
              extraKeyPart = "_ivy2Local"
            )
          }
        }
      }
    }

    test("properties") {

      val fetch0 = fetch
        .withRepositories(Seq(
          Repositories.central,
          mvn"http://repository.splicemachine.com/nexus/content/groups/public",
          mvn"http://repository.mapr.com/maven"
        ))
        .addDependencies(
          dep"com.splicemachine:splice_spark:2.8.0.1915-SNAPSHOT"
        )
        .mapResolutionParams(
          _.addForceVersion0(
            mod"org.apache.hadoop:hadoop-common" -> VersionConstraint("2.7.3")
          )
        )

      val prop = "env" -> "mapr6.1.0"

      // would be nice to have tests with different results, whether the properties
      // are forced or not

      test - async {
        val fetch = fetch0.mapResolutionParams(_.addForcedProperties(prop))
        val res = await {
          fetch.futureResult()
        }

        await(validateArtifacts(res.resolution, res.artifacts.map(_._1), fetch.resolutionParams))
      }

      test - async {
        val fetch = fetch0.mapResolutionParams(_.addProperties(prop))
        val res = await {
          fetch.futureResult()
        }

        await(validateArtifacts(res.resolution, res.artifacts.map(_._1), fetch.resolutionParams))
      }
    }

    test("publications") {
      test("ivy") {
        async {
          val artifactTypes = Seq(Type("info"))

          val res = await {
            fetch
              .withCache(cacheWithHandmadeMetadata)
              .withRepositories(Seq(
                Repositories.central,
                IvyRepository.parse("http://ivy.abc.com/[defaultPattern]").toOption.get
              ))
              .addDependencies(dep"test:a_2.12:1.0.0")
              .addArtifactTypes(artifactTypes: _*)
              .futureResult()
          }

          assert(res.artifacts.nonEmpty)
          assert(res.detailedArtifacts0.count(_._2.exists(_.ext == Extension("csv"))) == 1)

          await {
            validateArtifacts(
              res.resolution,
              res.artifacts.map(_._1),
              artifactTypes = artifactTypes.toSet
            )
          }
        }
      }
    }

    test("subset") {
      async {

        val res = await {
          fetch
            .addDependencies(dep"sh.almond:scala-kernel_2.12.8:0.7.0")
            .addRepositories(Repositories.jitpack)
            .futureResult()
        }

        await(validateArtifacts(res.resolution, res.artifacts.map(_._1)))

        val subsetRes = res.resolution.subset0(Seq(dep"sh.almond:scala-kernel-api_2.12.8")) match {
          case Left(ex)    => throw new Exception(ex)
          case Right(res0) => res0
        }

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

        await {
          validateArtifacts(
            subsetRes,
            subsetSourcesArtifacts.map(_._1),
            classifiers = Set(Classifier.sources)
          )
        }
      }
    }

    test("jai_core") {
      test("ko") {
        val cache1 = cache match {
          case cache: coursier.cache.MockCache[Task] =>
            cache.withDummyArtifact(_ => false)
        }
        try {
          val res = fetch
            .withCache(cache1)
            .addDependencies(dep"javax.media:jai_core:1.1.3")
            .run()
          println(s"res=$res")
          assert(false)
        }
        catch { case _: coursier.error.FetchError.DownloadingArtifacts => () }
      }

      test("ok") {
        async {
          val res = await {
            val osgeo = MavenRepository("https://repo.osgeo.org/repository/release")
            fetch
              .withRepositories(Seq(osgeo, Repositories.central))
              .addDependencies(dep"javax.media:jai_core:1.1.3")
              .future()
          }
          assert(res(0) != null)
        }
      }
    }

    test("url on lower version") {
      async {
        val res = await {
          fetch
            .addDependencies(
              dep"org.apache.commons:commons-compress:1.4.1",
              dep"org.apache.commons:commons-compress:1.5"
            )
            .addRepositories(
              InMemoryRepository.forDependencies(
                dep"org.apache.commons:commons-compress:1.4.1" ->
                  "https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar"
              )
            )
            .futureResult()
        }

        await {
          validateArtifacts(
            res.resolution,
            res.artifacts.map(_._1),
            extraKeyPart = "_customurl"
          )
        }
      }
    }

    test("url and force version") {
      async {
        val params = fetch.resolutionParams
          .addForceVersion0(mod"org.apache.commons:commons-compress" -> VersionConstraint("1.5"))
        val res = await {
          fetch
            .withResolutionParams(params)
            .addDependencies(
              dep"org.apache.commons:commons-compress:1.5"
            )
            .addRepositories(
              InMemoryRepository.forDependencies(
                dep"org.apache.commons:commons-compress:1.5" ->
                  "https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar"
              )
            )
            .futureResult()
        }

        await {
          validateArtifacts(
            res.resolution,
            res.artifacts.map(_._1),
            params,
            extraKeyPart = "_customurl2"
          )
        }
      }
    }

    test("gradle modules") {
      test("kotlinx-html-js") {
        test("no-support") {
          async {

            val res = await {
              fetch
                .addDependencies(
                  dep"org.jetbrains.kotlinx:kotlinx-html-js:0.11.0,variant.org.gradle.usage=kotlin-runtime,variant.org.jetbrains.kotlin.platform.type=js,variant.org.jetbrains.kotlin.js.compiler=ir,variant.org.gradle.category=library"
                )
                .futureResult()
            }

            await(validateArtifacts(res.resolution, res.artifacts.map(_._1)))
          }
        }

        test("support") {
          async {

            val res = await {
              enableModules(fetch)
                .addDependencies(
                  dep"org.jetbrains.kotlinx:kotlinx-html-js:0.11.0,variant.org.gradle.usage=kotlin-runtime,variant.org.jetbrains.kotlin.platform.type=js,variant.org.jetbrains.kotlin.js.compiler=ir,variant.org.gradle.category=library"
                )
                .futureResult()
            }

            await(validateArtifacts(
              res.resolution,
              res.artifacts.map(_._1),
              extraKeyPart = "_gradlemod"
            ))
          }
        }
      }

      test("android") {

        def withVariant(dep: Dependency, map: Map[String, VariantSelector.VariantMatcher]) =
          dep.withVariantSelector(VariantSelector.AttributesBased(map))

        def testVariants(map: Map[String, VariantSelector.VariantMatcher]): Future[Unit] = async {
          val params = fetch.resolutionParams
          val res = await {
            enableModules(fetch.addRepositories(Repositories.google))
              .withResolutionParams(params)
              .addDependencies(
                withVariant(dep"androidx.core:core-ktx:1.15.0", map),
                withVariant(dep"androidx.activity:activity-compose:1.9.3", map),
                withVariant(dep"androidx.compose.ui:ui:1.7.5", map),
                withVariant(dep"androidx.compose.material3:material3:1.3.1", map)
              )
              .futureResult()
          }

          await(validateArtifacts(
            res.resolution,
            res.artifacts.map(_._1),
            params = params,
            extraKeyPart = "_gradlemod"
          ))
        }

        test("compile") {
          testVariants(
            Map(
              "org.gradle.usage"    -> VariantSelector.VariantMatcher.Equals("java-api"),
              "org.gradle.category" -> VariantSelector.VariantMatcher.Equals("library"),
              "org.jetbrains.kotlin.platform.type" -> VariantSelector.VariantMatcher.Equals("jvm")
            )
          )
        }

        test("runtime") {
          testVariants(
            Map(
              "org.gradle.usage"    -> VariantSelector.VariantMatcher.Equals("java-runtime"),
              "org.gradle.category" -> VariantSelector.VariantMatcher.Equals("library"),
              "org.jetbrains.kotlin.platform.type" -> VariantSelector.VariantMatcher.Equals("jvm")
            )
          )
        }
      }

      test("fallback from config") {

        def testVariants(
          config: Option[Configuration] = None,
          defaultAttributes: Option[VariantSelector.AttributesBased] = None
        )(
          dependencies: Dependency*
        ): Future[Unit] = async {
          val params = fetch.resolutionParams
            .withDefaultConfiguration(
              config.getOrElse(fetch.resolutionParams.defaultConfiguration)
            )
            .withDefaultVariantAttributes(
              defaultAttributes.orElse(fetch.resolutionParams.defaultVariantAttributes)
            )
          val res = await {
            enableModules(fetch.addRepositories(Repositories.google))
              .withResolutionParams(params)
              .addDependencies(dependencies: _*)
              .futureResult()
          }

          await(validateArtifacts(
            res.resolution,
            res.artifacts.map(_._1),
            params = params,
            extraKeyPart = "_gradlemod"
          ))
        }

        test("compile") {
          val attr = VariantSelector.AttributesBased().withMatchers(
            Map(
              "org.jetbrains.kotlin.platform.type" -> VariantSelector.VariantMatcher.Equals("jvm")
            )
          )
          testVariants(Some(Configuration.compile), defaultAttributes = Some(attr))(
            dep"androidx.core:core-ktx:1.15.0:compile"
          )
        }
        test("runtime") {
          val attr = VariantSelector.AttributesBased().withMatchers(
            Map(
              "org.jetbrains.kotlin.platform.type" -> VariantSelector.VariantMatcher.Equals("jvm")
            )
          )
          testVariants(defaultAttributes = Some(attr))(
            dep"androidx.core:core-ktx:1.15.0"
          )
        }
      }
    }
  }
}
