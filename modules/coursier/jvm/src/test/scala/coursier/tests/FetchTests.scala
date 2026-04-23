package coursier.tests

import coursier.{Artifacts, Fetch, Repositories}
import coursier.core.{Classifier, Configuration, Dependency, Extension, Type, VariantSelector}
import coursier.maven.{MavenRepository, MavenRepositoryLike}
import coursier.ivy.IvyRepository
import coursier.util.StringInterpolators._
import coursier.util.{InMemoryRepository, Task}
import coursier.version.VersionConstraint
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

    /** Verifies the `artifactTypes` scenario behaves as the user expects. */
    test("artifactTypes") {
      /** Verifies the `default` scenario behaves as the user expects. */
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

      /** Verifies the `sources` scenario behaves as the user expects. */
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

      /** Verifies the `mainAndSources` scenario behaves as the user expects. */
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

      /** Verifies the `javadoc` scenario behaves as the user expects. */
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

      /** Verifies the `mainAndJavadoc` scenario behaves as the user expects. */
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

      /** Verifies the `sourcesAndJavadoc` scenario behaves as the user expects. */
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

      /** Verifies the `exotic` scenario behaves as the user expects. */
      test("exotic") {
        /** Verifies the `orbit` scenario behaves as the user expects. */
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

        /** Verifies the `klib` scenario behaves as the user expects. */
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

    /** Verifies the `testScope` scenario behaves as the user expects. */
    test("testScope") {

      val m2Local   = handmadeMetadataBase + "http/abc.com"
      val ivy2Local = handmadeMetadataBase + "http/ivy.abc.com"

      val m2Repo   = MavenRepository(m2Local)
      val ivy2Repo = IvyRepository.parse(ivy2Local + "/[defaultPattern]").toOption.get

      val fetch0 = fetch
        .withRepositories(Seq(Repositories.central))

      /** Verifies the `m2Local` scenario behaves as the user expects. */
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

      /** Verifies the `ivy2Local` scenario behaves as the user expects. */
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

    /** Verifies the `properties` scenario behaves as the user expects. */
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

    /** Verifies the `publications` scenario behaves as the user expects. */
    test("publications") {
      /** Verifies the `ivy` scenario behaves as the user expects. */
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

    /** Verifies the `subset` scenario behaves as the user expects. */
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

    /** Verifies the `jai_core` scenario behaves as the user expects. */
    test("jai_core") {
      /** Verifies the `ko` scenario behaves as the user expects. */
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

      /** Verifies the `ok` scenario behaves as the user expects. */
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

    /** Verifies the `url on lower version` scenario behaves as the user expects. */
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

    /** Verifies the `url and force version` scenario behaves as the user expects. */
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

    /** Verifies the `gradle modules` scenario behaves as the user expects. */
    test("gradle modules") {
      /** Verifies the `kotlinx-html-js` scenario behaves as the user expects. */
      test("kotlinx-html-js") {
        /** Verifies the `no-support` scenario behaves as the user expects. */
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

        /** Verifies the `support` scenario behaves as the user expects. */
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

      /** Verifies the `android` scenario behaves as the user expects. */
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

        /** Verifies the `compile` scenario behaves as the user expects. */
        test("compile") {
          testVariants(
            Map(
              "org.gradle.usage"    -> VariantSelector.VariantMatcher.Equals("java-api"),
              "org.gradle.category" -> VariantSelector.VariantMatcher.Equals("library"),
              "org.jetbrains.kotlin.platform.type" -> VariantSelector.VariantMatcher.Equals("jvm")
            )
          )
        }

        /** Verifies the `runtime` scenario behaves as the user expects. */
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

      /** Verifies the `fallback from config` scenario behaves as the user expects. */
      test("fallback from config") {

        def testVariants(
          config: Option[Configuration] = None,
          defaultAttributes: Option[VariantSelector.AttributesBased]
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

        /** Verifies the `compile` scenario behaves as the user expects. */
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
        /** Verifies the `runtime` scenario behaves as the user expects. */
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
      /** Verifies the `sources` scenario behaves as the user expects. */
      test("sources") {
        /** Verifies the `compile` scenario behaves as the user expects. */
        test("compile") {
          async {
            val params = fetch.resolutionParams
              .withDefaultConfiguration(Configuration.compile)
              .addVariantAttributes(
                "org.gradle.jvm.environment" ->
                  VariantSelector.VariantMatcher.Equals("standard-jvm"),
                "org.jetbrains.kotlin.platform.type" ->
                  VariantSelector.VariantMatcher.Equals("jvm")
              )
            val classifiers = Set(Classifier.sources)
            val attr        = Seq(VariantSelector.AttributesBased.sources)
            val res = await {
              enableModules(fetch.addRepositories(Repositories.google))
                .addDependencies(dep"org.jetbrains.kotlin:kotlin-stdlib:2.1.20")
                .withClassifiers(classifiers)
                .withArtifactAttributes(attr)
                .withResolutionParams(params)
                .futureResult()
            }

            await(validateArtifacts(
              res.resolution,
              res.artifacts.map(_._1),
              classifiers = classifiers,
              artifactAttributes = attr,
              params = params
            ))
          }
        }

        /** Verifies the `default` scenario behaves as the user expects. */
        test("default") {
          async {
            val params = fetch.resolutionParams.addVariantAttributes(
              "org.jetbrains.kotlin.platform.type" ->
                VariantSelector.VariantMatcher.AnyOf(Seq(
                  VariantSelector.VariantMatcher.Equals("androidJvm"),
                  VariantSelector.VariantMatcher.Equals("jvm")
                ))
            )
            val classifiers = Set(Classifier.sources)
            val attr        = Seq(VariantSelector.AttributesBased.sources)
            val res = await {
              enableModules(fetch.addRepositories(Repositories.google))
                .addDependencies(dep"androidx.compose.material3:material3:1.3.1")
                .withClassifiers(classifiers)
                .withArtifactAttributes(attr)
                .withResolutionParams(params)
                .futureResult()
            }

            await(validateArtifacts(
              res.resolution,
              res.artifacts.map(_._1),
              classifiers = classifiers,
              artifactAttributes = attr,
              params = params
            ))
          }
        }
      }
    }
  }
}
