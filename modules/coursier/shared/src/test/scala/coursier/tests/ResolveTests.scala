package coursier.tests

import coursier.{Repositories, Resolve}
import coursier.core.{
  Activation,
  BomDependency,
  Classifier,
  Configuration,
  Dependency,
  Extension,
  Module,
  ModuleName,
  Repository,
  Resolution,
  Type,
  VariantSelector
}
import coursier.core.VariantSelector.VariantMatcher
import coursier.error.ResolutionError
import coursier.ivy.IvyRepository
import coursier.maven.{MavenRepository, MavenRepositoryLike}
import coursier.params.{MavenMirror, Mirror, ResolutionParams, TreeMirror}
import coursier.util.{ModuleMatchers, Task}
import coursier.util.StringInterpolators._
import coursier.version.{ConstraintReconciliation, Version, VersionConstraint}
import utest._

import scala.async.Async.{async, await}
import scala.collection.compat._
import scala.concurrent.Future

object ResolveTests extends TestSuite {

  import TestHelpers.{ec, cache, handmadeMetadataBase, validateDependencies, versionOf}

  private val resolve = Resolve()
    .noMirrors
    .withCache(cache)

  def doCheck(resolve: Resolve[Task], dependencies: Seq[Dependency]): Future[Unit] =
    async {
      val res = await {
        resolve
          .addDependencies(dependencies: _*)
          .future()
      }
      await(validateDependencies(res, resolve.resolutionParams))
    }

  def check(dependencies: Dependency*): Future[Unit] =
    doCheck(resolve, dependencies)

  def enableModules(resolve: Resolve[Task]): Resolve[Task] =
    resolve.withRepositories {
      resolve.repositories.map {
        case m: MavenRepositoryLike.WithModuleSupport =>
          m.withCheckModule(true)
        case other => other
      }
    }
  def gradleModuleCheck0(
    resolve0: Resolve[Task] = resolve,
    defaultConfiguration: Option[Configuration] = None,
    defaultAttributes: Option[VariantSelector.AttributesBased] = None,
    attributesBasedReprAsToString: Boolean = false
  )(
    dependencies: Dependency*
  ): Future[Unit] =
    async {
      var resolve1 = enableModules(resolve0.addRepositories(Repositories.google))
      for (conf <- defaultConfiguration)
        resolve1 = resolve1.mapResolutionParams(_.withDefaultConfiguration(conf))
      for (attr <- defaultAttributes)
        resolve1 = resolve1.mapResolutionParams(_.withDefaultVariantAttributes(attr))
      val res = await {
        resolve1
          .addDependencies(dependencies: _*)
          .future()
      }
      await {
        validateDependencies(
          res,
          resolve1.resolutionParams,
          extraKeyPart = "_gradlemod",
          attributesBasedReprAsToString = attributesBasedReprAsToString
        )
      }
    }
  def gradleModuleCheck(dependencies: Dependency*): Future[Unit] =
    gradleModuleCheck0()(dependencies: _*)

  def scopeCheck(
    defaultConfiguration: Configuration,
    extraRepositories: Seq[Repository]
  )(
    dependencies: Dependency*
  ): Future[Unit] =
    async {
      val resolve0 = resolve
        .addDependencies(dependencies: _*)
        .addRepositories(extraRepositories: _*)
        .mapResolutionParams(_.withDefaultConfiguration(defaultConfiguration))
      val res = await {
        resolve0.future()
      }
      await(validateDependencies(res, resolve0.resolutionParams))
    }

  def bomCheck(boms: BomDependency*)(dependencies: Dependency*): Future[Unit] =
    async {
      val res = await {
        resolve
          .addDependencies(dependencies: _*)
          .addBomConfigs(boms: _*)
          .future()
      }
      await(validateDependencies(res))
    }

  val tests = Tests {

    test("simple") {
      async {

        val res = await {
          resolve
            .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
            .future()
        }

        await(validateDependencies(res))
      }
    }

    test("simple sttp with forced Scala 3") {
      async {

        val resolve0 = resolve
          .addDependencies(dep"com.softwaremill.sttp.client3:core_3.0.0-RC1:3.1.7")
          .mapResolutionParams { params =>
            params
              .withScalaVersion("3.0.0-RC1")
          }
        val res = await {
          resolve0
            .future()
        }

        await(validateDependencies(res, resolve0.resolutionParams))
      }
    }

    test("forceScalaVersion") {
      async {

        val resolve0 = resolve
          .addDependencies(dep"sh.almond:scala-kernel_2.12.7:0.2.2")
          .addRepositories(Repositories.jitpack)
          .mapResolutionParams { params =>
            params
              .withScalaVersion("2.12.7")
          }

        val res = await {
          resolve0
            .future()
        }

        await(validateDependencies(res, resolve0.resolutionParams))
      }
    }

    test("typelevel") {
      async {

        val resolve0 = resolve
          .addDependencies(dep"com.lihaoyi:ammonite_2.11.8:1.6.3")
          .mapResolutionParams { params =>
            params
              .withScalaVersion("2.11.8")
              .withTypelevel(true)
          }

        val res = await {
          resolve0
            .future()
        }

        await(validateDependencies(res, resolve0.resolutionParams))
      }
    }

    test("addForceVersion") {
      test("simple") {
        async {

          val resolve0 = resolve
            .addDependencies(dep"com.lihaoyi:ammonite_2.12.8:1.6.3")
            .mapResolutionParams { params =>
              params
                .withScalaVersion("2.12.8")
                .addForceVersion0(mod"com.lihaoyi:upickle_2.12" -> VersionConstraint("0.7.0"))
                .addForceVersion0(
                  mod"io.get-coursier:coursier_2.12" -> VersionConstraint("1.1.0-M6")
                )
            }

          val res = await {
            resolve0
              .future()
          }

          await(validateDependencies(res, resolve0.resolutionParams))

          val upickleVersionOpt      = versionOf(res, mod"com.lihaoyi:upickle_2.12")
          val expectedUpickleVersion = "0.7.0"
          assert(upickleVersionOpt.contains(expectedUpickleVersion))

          val coursierVersionOpt      = versionOf(res, mod"io.get-coursier:coursier_2.12")
          val expectedCoursierVersion = "1.1.0-M6"
          assert(coursierVersionOpt.contains(expectedCoursierVersion))
        }
      }

      test("whole org") {
        async {
          val forcedCollectionVersion = "1.4.2"
          val forcedLifecycleVersion  = "2.8.7"
          val baseResolve = enableModules(resolve.addRepositories(Repositories.google))
            .addDependencies(dep"androidx.customview:customview-poolingcontainer:1.0.0")
            .mapResolutionParams { params =>
              params.addVariantAttributes(
                "org.jetbrains.kotlin.platform.type" ->
                  VariantMatcher.AnyOf(Seq(
                    VariantMatcher.Equals("androidJvm"),
                    VariantMatcher.Equals("jvm")
                  ))
              )
            }
          val resolve0 = baseResolve
            .mapResolutionParams { params =>
              params.addForceVersion0(
                mod"androidx.collection:*" -> VersionConstraint(forcedCollectionVersion),
                mod"androidx.lifecycle:*"  -> VersionConstraint(forcedLifecycleVersion)
              )
            }

          val baseRes = await {
            baseResolve
              .future()
          }
          val res = await {
            resolve0
              .future()
          }

          await(validateDependencies(baseRes, baseResolve.resolutionParams))
          await(validateDependencies(res, resolve0.resolutionParams))

          val baseCollectionVersionOpt = versionOf(baseRes, mod"androidx.collection:collection")
          val collectionVersionOpt     = versionOf(res, mod"androidx.collection:collection")
          assert(baseCollectionVersionOpt.exists(_ != forcedCollectionVersion))
          assert(collectionVersionOpt.contains(forcedCollectionVersion))

          val baseLifecycleCommonVersionOpt =
            versionOf(baseRes, mod"androidx.lifecycle:lifecycle-common")
          val lifecycleCommonVersionOpt = versionOf(res, mod"androidx.lifecycle:lifecycle-common")
          assert(baseLifecycleCommonVersionOpt.exists(_ != forcedLifecycleVersion))
          assert(lifecycleCommonVersionOpt.contains(forcedLifecycleVersion))

          val baseLifecycleRuntimeVersionOpt =
            versionOf(baseRes, mod"androidx.lifecycle:lifecycle-runtime")
          val lifecycleRuntimeVersionOpt = versionOf(res, mod"androidx.lifecycle:lifecycle-runtime")
          assert(baseLifecycleRuntimeVersionOpt.exists(_ != forcedLifecycleVersion))
          assert(lifecycleRuntimeVersionOpt.contains(forcedLifecycleVersion))
        }
      }
    }

    test("mirrors") {

      def run(mirror: Mirror) = async {
        val res = await {
          resolve
            .addMirrors(mirror)
            .addDependencies(dep"com.github.alexarchambault:argonaut-shapeless_6.2_2.12:1.2.0-M10")
            .future()
        }

        await(validateDependencies(res))

        val artifacts = res.artifacts()
        assert(artifacts.forall(_.url.startsWith("https://jcenter.bintray.com")))
      }

      test("mavenMirror") {
        test("specific") {
          run {
            MavenMirror(
              "https://jcenter.bintray.com",
              "https://repo1.maven.org/maven2"
            )
          }
        }
        test("all") {
          run(MavenMirror("https://jcenter.bintray.com", "*"))
        }

        test("trailingSlash") {
          test("specific") {
            test {
              run {
                MavenMirror(
                  "https://jcenter.bintray.com/",
                  "https://repo1.maven.org/maven2"
                )
              }
            }
            test {
              run {
                MavenMirror(
                  "https://jcenter.bintray.com",
                  "https://repo1.maven.org/maven2/"
                )
              }
            }
            test {
              run {
                MavenMirror(
                  "https://jcenter.bintray.com/",
                  "https://repo1.maven.org/maven2/"
                )
              }
            }
          }
          test("all") {
            run(MavenMirror("https://jcenter.bintray.com/", "*"))
          }
        }
      }

      test("treeMirror") {
        test {
          run(TreeMirror("https://jcenter.bintray.com", "https://repo1.maven.org/maven2"))
        }
        test("trailingSlash") {
          test {
            run(TreeMirror("https://jcenter.bintray.com/", "https://repo1.maven.org/maven2"))
          }
          test {
            run(TreeMirror("https://jcenter.bintray.com", "https://repo1.maven.org/maven2/"))
          }
          test {
            run(TreeMirror("https://jcenter.bintray.com/", "https://repo1.maven.org/maven2/"))
          }
        }
      }
    }

    test("latest") {
      test("maven") {

        val resolve0 = resolve
          .withRepositories(Seq(
            Repositories.sonatype("snapshots"),
            Repositories.central
          ))

        test("integration") {
          async {

            val res = await {
              resolve0
                .addDependencies(dep"com.chuusai:shapeless_2.12:latest.integration")
                .future()
            }

            await(validateDependencies(res))
          }
        }

        test("release") {
          async {

            val res = await {
              resolve0
                .addDependencies(dep"com.chuusai:shapeless_2.12:latest.release")
                .future()
            }

            await(validateDependencies(res))
          }
        }

        test("stable") {
          async {

            val res = await {
              resolve0
                .addDependencies(dep"com.lihaoyi:ammonite_2.12.8:latest.stable")
                .future()
            }

            val found         = res.orderedDependencies.map(_.moduleVersionConstraint).toMap
            val ammVersionOpt = found.get(mod"com.lihaoyi:ammonite_2.12.8").map(_.asString)
            assert(ammVersionOpt.exists(_.split('.').length == 3))
            assert(ammVersionOpt.exists(!_.contains("-")))

            await(validateDependencies(res))
          }
        }

        test("withInterval") {
          test("in") {
            async {

              val res = await {
                resolve0
                  .addDependencies(
                    dep"com.chuusai:shapeless_2.10:latest.release",
                    dep"com.chuusai:shapeless_2.10:2.3+"
                  )
                  .future()
              }

              await(validateDependencies(res))
            }
          }

          test("out") {
            async {

              val res = await {
                resolve0
                  .addDependencies(
                    dep"com.chuusai:shapeless_2.10:latest.release",
                    dep"com.chuusai:shapeless_2.10:[2.3.0,2.3.3)"
                  )
                  .io
                  .attempt
                  .future()
              }

              val isLeft = res.isLeft
              assert(isLeft)

              val error = res.swap.toOption.get

              error match {
                case e: ResolutionError.CantDownloadModule =>
                  assert(e.module == mod"com.chuusai:shapeless_2.10")
                  val lines = e.getMessage.linesIterator.toVector.map(_.trim)
                  assert(lines.contains("No version for latest.release available in [2.3.0,2.3.3)"))
                case _ =>
                  throw error
              }
            }
          }
        }
      }

      test("ivy") {

        val resolve0 = resolve
          .withRepositories(Seq(
            Repositories.central,
            IvyRepository.parse(handmadeMetadataBase + "http/ivy.abc.com/[defaultPattern]")
              .fold(sys.error, identity)
          ))

        test("integration") {
          async {

            val res = await {
              resolve0
                .addDependencies(dep"test:a_2.12:latest.integration")
                .future()
            }

            val found = res.orderedDependencies.map(_.moduleVersionConstraint).toSet
            val expected = Set(
              mod"org.scala-lang:scala-library" -> VersionConstraint("2.12.8"),
              mod"test:a_2.12"                  -> VersionConstraint("1.0.2-SNAPSHOT")
            )

            if (found != expected) {
              pprint.err.log(expected)
              pprint.err.log(found)
            }
            assert(found == expected)
          }
        }

        test("release") {
          async {

            val res = await {
              resolve0
                .addDependencies(dep"test:a_2.12:latest.release")
                .future()
            }

            val found = res.orderedDependencies.map(_.moduleVersionConstraint).toSet
            val expected = Set(
              mod"org.scala-lang:scala-library" -> VersionConstraint("2.12.8"),
              mod"test:a_2.12"                  -> VersionConstraint("1.0.1")
            )

            assert(found == expected)
          }
        }
      }
    }

    test("ivyLatestSubRevision") {
      test("zero") {
        test {
          async {

            val res = await {
              resolve
                .addDependencies(dep"io.circe:circe-core_2.12:0+")
                .future()
            }

            await(validateDependencies(res))
          }
        }

        test {
          async {

            val res = await {
              resolve
                .addDependencies(dep"io.circe:circe-core_2.12:0.11+")
                .future()
            }

            await(validateDependencies(res))
          }
        }
      }

      test("nonZero") {
        async {

          val res = await {
            resolve
              .addDependencies(dep"com.chuusai:shapeless_2.12:2.3+")
              .future()
          }

          await(validateDependencies(res))
        }
      }

      test("plusInVersion") {
        async {

          val resolve0 = resolve
            .withRepositories(Seq(
              Repositories.central,
              IvyRepository.parse(handmadeMetadataBase + "http/ivy.abc.com/[defaultPattern]")
                .fold(sys.error, identity)
            ))

          val res = await {
            resolve0
              .addDependencies(dep"test:b_2.12:latest.release")
              .future()
          }

          val found = res.orderedDependencies.map(_.moduleVersionConstraint).toSet
          val expected = Set(
            mod"org.scala-lang:scala-library" -> VersionConstraint("2.12.8"),
            mod"test:b_2.12"                  -> VersionConstraint("1.0.2+20190524-1")
          )

          assert(found == expected)
        }
      }
    }

    test("exclusions") {

      val resolve0 = resolve
        .addDependencies(dep"com.github.alexarchambault:argonaut-shapeless_6.2_2.12:1.2.0-M11")

      test("check") {
        async {
          val res = await {
            resolve0
              .future()
          }

          val argonaut = res.minDependencies.filter(_.module.organization == org"io.argonaut")
          assert(argonaut.nonEmpty)

          await(validateDependencies(res))
        }
      }

      test("test") {
        async {
          val resolve = resolve0
            .mapResolutionParams(_.addExclusions((org"io.argonaut", name"*")))
          val res = await {
            resolve.future()
          }

          val argonaut = res.minDependencies.filter(_.module.organization == org"io.argonaut")
          assert(argonaut.isEmpty)

          await(validateDependencies(res, resolve.resolutionParams))
        }
      }

      test("no org") {
        async {

          val res = await {
            resolve
              .addDependencies(
                dep"com.netflix.karyon:karyon-eureka:1.0.28"
                  .withVariantSelector(
                    VariantSelector.ConfigurationBased(Configuration.defaultRuntime)
                  )
              )
              .future()
          }

          await(validateDependencies(res))
        }
      }
    }

    test("beam") {
      test("default") {
        async {

          val res = await {
            resolve
              .addDependencies(dep"org.apache.beam:beam-sdks-java-io-google-cloud-platform:2.3.0")
              .future()
          }

          await(validateDependencies(res))
        }
      }

      test("compile") {
        async {

          val resolve0 = resolve
            .withResolutionParams(
              resolve.resolutionParams
                .withDefaultConfiguration(Configuration.compile)
            )

          val res = await {
            resolve0
              .addDependencies(dep"org.apache.beam:beam-sdks-java-io-google-cloud-platform:2.3.0")
              .future()
          }

          await(validateDependencies(res, resolve0.resolutionParams))
        }
      }

      test("conflict") {
        async {

          val res = await {
            resolve
              .addDependencies(dep"org.apache.beam:beam-sdks-java-io-google-cloud-platform:2.3.0")
              .withResolutionParams(
                resolve.resolutionParams
                  .withEnableDependencyOverrides(Some(false))
              )
              .io
              .attempt
              .future()
          }

          val isLeft = res.isLeft
          assert(isLeft)

          val error = res.swap.toOption.get
          error match {
            case c: ResolutionError.ConflictingDependencies =>
              val expectedModules = Set(mod"io.grpc:grpc-core")
              val modules         = c.dependencies.map(_.module)
              assert(modules == expectedModules)
              val expectedVersions = Map(
                mod"io.grpc:grpc-core" -> Set("1.2.0", "1.6.1", "1.7.0", "[1.2.0]", "[1.7.0]")
              )
              val versions = c
                .dependencies
                .groupBy(_.module)
                .view
                .mapValues(_.map(_.versionConstraint.asString))
                .iterator
                .toMap
              assert(versions == expectedVersions)
            case _ =>
              throw new Exception("Unexpected exception", error)
          }
        }
      }
    }

    test("parent / import scope") {
      test {
        async {

          val res = await {
            resolve
              .addDependencies(
                dep"software.amazon.awssdk:utils:2.5.17",
                dep"software.amazon.awssdk:auth:2.5.17"
              )
              .future()
          }

          await(validateDependencies(res))
        }
      }
    }

    test("properties") {
      test("in packaging") {
        async {
          val res = await {
            resolve
              .addDependencies(dep"javax.ws.rs:javax.ws.rs-api:2.1.1")
              .future()
          }

          await(validateDependencies(res))

          val urls = res.dependencyArtifacts0().map(_._3.url).toSet
          val expectedUrls = Set(
            "https://repo1.maven.org/maven2/javax/ws/rs/javax.ws.rs-api/2.1.1/javax.ws.rs-api-2.1.1.jar"
          )

          assert(urls == expectedUrls)
        }
      }
    }

    test("ivy") {
      test("publication name") {
        async {

          val resolve0 = resolve
            .withRepositories(Seq(
              Repositories.central,
              IvyRepository.parse(handmadeMetadataBase + "http/ivy.abc.com/[defaultPattern]")
                .fold(sys.error, identity)
            ))

          val deps = Seq(
            dep"test:b_2.12:1.0.1".withPublication("foo"),
            dep"test:b_2.12:1.0.1".withPublication("bzz", Type("zip"))
          )

          val res = await {
            resolve0
              .addDependencies(deps: _*)
              .future()
          }

          val found = res.orderedDependencies.map(_.moduleVersionConstraint).toSet
          val expected = Set(
            mod"org.scala-lang:scala-library" -> VersionConstraint("2.12.8"),
            mod"test:b_2.12"                  -> VersionConstraint("1.0.1")
          )

          assert(found == expected)

          val urls = res.dependencyArtifacts0()
            .map(_._3.url.replace(handmadeMetadataBase, "file:///handmade-metadata/"))
            .toSet
          val expectedUrls = Set(
            "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.12.8/scala-library-2.12.8.jar",
            "file:///handmade-metadata/http/ivy.abc.com/test/b_2.12/1.0.1/jars/foo.jar",
            "file:///handmade-metadata/http/ivy.abc.com/test/b_2.12/1.0.1/zips/bzz.zip"
          )

          assert(urls == expectedUrls)

          val handmadeArtifacts = res.dependencyArtifacts0()
            .map(_._3)
            .filter(_.url.startsWith(handmadeMetadataBase))

          assert(handmadeArtifacts.nonEmpty)
          assert(handmadeArtifacts.forall(!_.optional))
        }
      }

      test("global excludes") {
        async {

          val resolve0 = resolve
            .withRepositories(Seq(
              Repositories.central,
              IvyRepository.parse(handmadeMetadataBase + "fake-ivy/[defaultPattern]")
                .fold(sys.error, identity)
            ))

          val res = await {
            resolve0
              .addDependencies(
                dep"io.get-coursier.test:sbt-coursier-exclude-dependencies-2_2.12:0.1.0-SNAPSHOT"
              )
              .future()
          }

          await(validateDependencies(res))

          val urls = res.dependencyArtifacts0()
            .map(_._3.url.replace(handmadeMetadataBase, "file:///handmade-metadata/"))
            .toSet
          val expectedUrls = Set(
            "file:///handmade-metadata/fake-ivy/io.get-coursier.test/sbt-coursier-exclude-dependencies-2_2.12/0.1.0-SNAPSHOT/jars/sbt-coursier-exclude-dependencies-2_2.12.jar",
            "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.12.8/scala-library-2.12.8.jar",
            "https://repo1.maven.org/maven2/com/github/alexarchambault/argonaut-shapeless_6.2_2.12/1.2.0-M11/argonaut-shapeless_6.2_2.12-1.2.0-M11.jar"
          )

          assert(urls == expectedUrls)
        }
      }

      test("global overrides") {
        async {

          val resolve0 = resolve
            .withRepositories(Seq(
              Repositories.central,
              IvyRepository.parse(handmadeMetadataBase + "fake-ivy/[defaultPattern]")
                .fold(sys.error, identity)
            ))

          val res = await {
            resolve0
              .addDependencies(
                dep"io.get-coursier.test:sbt-coursier-override-dependencies-2_2.12:0.1.0-SNAPSHOT"
              )
              .future()
          }

          await(validateDependencies(res))

          val urls = res.dependencyArtifacts0()
            .map(_._3.url.replace(handmadeMetadataBase, "file:///handmade-metadata/"))
            .toSet
          val expectedUrls = Set(
            "https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.12.6/scala-reflect-2.12.6.jar",
            "file:///handmade-metadata/fake-ivy/io.get-coursier.test/sbt-coursier-override-dependencies-2_2.12/0.1.0-SNAPSHOT/jars/sbt-coursier-override-dependencies-2_2.12.jar",
            "https://repo1.maven.org/maven2/com/chuusai/shapeless_2.12/2.3.9/shapeless_2.12-2.3.9.jar",
            "https://repo1.maven.org/maven2/io/argonaut/argonaut_2.12/6.2.2/argonaut_2.12-6.2.2.jar",
            "https://repo1.maven.org/maven2/com/github/alexarchambault/argonaut-shapeless_6.2_2.12/1.2.0-M11/argonaut-shapeless_6.2_2.12-1.2.0-M11.jar",
            "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.12.15/scala-library-2.12.15.jar"
          )

          if (urls != expectedUrls) {
            pprint.err.log(expectedUrls)
            pprint.err.log(urls)
          }

          assert(urls == expectedUrls)
        }
      }
    }

    test("version intervals") {
      test("0 lower bound") {
        async {

          val res = await {
            resolve
              .addDependencies(dep"org.webjars.npm:dom-serializer:[0,1)")
              .future()
          }

          await(validateDependencies(res))
        }
      }

      test("conflict with specific version") {
        test {
          async {

            val res = await {
              resolve
                .addDependencies(
                  dep"org.scala-lang:scala-library:2.12+",
                  dep"org.scala-lang:scala-library:2.13.0"
                )
                .io
                .attempt
                .future()
            }

            val isLeft = res.isLeft
            assert(isLeft)

            val error = res.swap.toOption.get

            error match {
              case c: ResolutionError.ConflictingDependencies =>
                val expectedModules = Set(mod"org.scala-lang:scala-library")
                val modules         = c.dependencies.map(_.module)
                assert(modules == expectedModules)
                val expectedVersions = Set("2.12+", "2.13.0")
                val versions         = c.dependencies.map(_.versionConstraint.asString)
                assert(versions == expectedVersions)
              case _ =>
                throw new Exception(error)
            }
          }
        }

        test {
          async {

            val res = await {
              resolve
                .addDependencies(
                  dep"com.github.alexarchambault:argonaut-shapeless_6.2_2.12:1.2.0-M11",
                  dep"com.chuusai:shapeless_2.12:[2.3.0,2.3.3)"
                )
                .io
                .attempt
                .future()
            }

            val isLeft = res.isLeft
            assert(isLeft)

            val error = res.swap.toOption.get

            error match {
              case c: ResolutionError.ConflictingDependencies =>
                val expectedModules = Set(mod"com.chuusai:shapeless_2.12")
                val modules         = c.dependencies.map(_.module)
                assert(modules == expectedModules)
                val expectedVersions = Set("[2.3.0,2.3.3)", "2.3.3")
                val versions         = c.dependencies.map(_.versionConstraint.asString)
                assert(versions == expectedVersions)
              case _ =>
                throw error
            }
          }
        }
      }
    }

    test("override dependency from profile") {

      test {
        async {

          val resolve0 = resolve
            .mapResolutionParams { params =>
              params
                .withUseSystemOsInfo(false)
                .withUseSystemJdkVersion(false)
            }
            .addDependencies(dep"io.netty:netty-transport-native-epoll:4.1.34.Final")

          val res = await {
            resolve0
              .future()
          }

          val unixCommonDepOpt =
            res.minDependencies.find(_.module == mod"io.netty:netty-transport-native-unix-common")
          assert(unixCommonDepOpt.exists(!_.optional))

          await(validateDependencies(res, resolve0.resolutionParams))
        }
      }

      test {
        async {

          val resolve0 = resolve
            .mapResolutionParams(_
              .withUseSystemOsInfo(false)
              .withUseSystemJdkVersion(false)
              .withOsInfo(coursier.core.Activation.Os.fromProperties(Map(
                "os.name"        -> "Linux",
                "os.arch"        -> "amd64",
                "os.version"     -> "4.9.125",
                "path.separator" -> ":"
              ))))
            .addDependencies(dep"io.netty:netty-transport-native-epoll:4.1.34.Final")

          val res = await {
            resolve0
              .future()
          }

          val unixCommonDepOpt =
            res.minDependencies.find(_.module == mod"io.netty:netty-transport-native-unix-common")
          assert(unixCommonDepOpt.exists(!_.optional))

          await(validateDependencies(res, resolve0.resolutionParams))
        }
      }

      test {
        async {

          val resolve0 = resolve
            .mapResolutionParams(_
              .withUseSystemOsInfo(false)
              .withUseSystemJdkVersion(false)
              .withOsInfo(coursier.core.Activation.Os.fromProperties(Map(
                "os.name"        -> "Mac OS X",
                "os.arch"        -> "x86_64",
                "os.version"     -> "10.14.5",
                "path.separator" -> ":"
              ))))
            .addDependencies(dep"io.netty:netty-transport-native-epoll:4.1.34.Final")

          val res = await {
            resolve0
              .future()
          }

          val unixCommonDepOpt =
            res.minDependencies.find(_.module == mod"io.netty:netty-transport-native-unix-common")
          assert(unixCommonDepOpt.exists(!_.optional))

          await(validateDependencies(res, resolve0.resolutionParams))
        }
      }
    }

    test("runtime dependencies") {
      async {

        // default configuration "default(runtime)" should fetch runtime JARs too

        val res: coursier.core.Resolution = await {
          resolve
            .addDependencies(dep"com.almworks.sqlite4java:libsqlite4java-linux-amd64:1.0.392")
            .future()
        }

        await(validateDependencies(res))

        val artifacts = res.artifacts(types = Resolution.defaultTypes + Type("so"))
        val urls      = artifacts.map(_.url).toSet
        val expectedUrls = Set(
          "https://repo1.maven.org/maven2/com/almworks/sqlite4java/sqlite4java/1.0.392/sqlite4java-1.0.392.jar",
          "https://repo1.maven.org/maven2/com/almworks/sqlite4java/libsqlite4java-linux-amd64/1.0.392/libsqlite4java-linux-amd64-1.0.392.so",
          // this one doesn't exist, but should be marked as optional anyway
          "https://repo1.maven.org/maven2/com/almworks/sqlite4java/libsqlite4java-linux-amd64/1.0.392/libsqlite4java-linux-amd64-1.0.392.jar"
        )

        assert(urls == expectedUrls)
      }
    }

    test("relaxed reconciliation") {
      test {
        async {
          val params = ResolutionParams()
            .withScalaVersion("2.12.8")
            .withReconciliation0(Seq(ModuleMatchers.all -> ConstraintReconciliation.Relaxed))

          val res = await {
            resolve
              .addDependencies(
                dep"org.webjars.npm:randomatic:1.1.7",
                dep"org.webjars.npm:is-odd:2.0.0"
              )
              .withResolutionParams(params)
              .future()
          }

          await(validateDependencies(res, params))

          val deps = res.minimizedDependencies(
            withRetainedVersions = false,
            withReconciledVersions = true
          )
          val isNumberVersions = deps.collect {
            case dep if dep.module == mod"org.webjars.npm:is-number" =>
              dep.versionConstraint.asString
          }
          val expectedIsNumberVersions = Set("[4.0.0,5)")
          assert(isNumberVersions == expectedIsNumberVersions)
        }
      }
    }

    test("subset") {
      async {
        val json4s = dep"org.json4s:json4s-native_2.12:[3.3.0,3.5.0)"
        val res = await {
          resolve
            .addDependencies(
              json4s,
              dep"org.scala-lang:scala-compiler:2.12.8"
            )
            .future()
        }

        await(validateDependencies(res))

        val subRes = res.subset0(Seq(json4s)) match {
          case Left(ex)    => throw new Exception(ex)
          case Right(res0) => res0
        }
        await(validateDependencies(subRes))
      }
    }

    test("initial resolution") {
      async {

        val res0 = await {
          resolve
            .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
            .future()
        }

        await(validateDependencies(res0))

        val res1 = await {
          resolve
            .withInitialResolution(Some(res0))
            .addRepositories(Repositories.typesafeIvy("releases"))
            .addDependencies(dep"io.get-coursier:coursier-cli_2.12:2.0.0-RC6-16")
            .future()
        }

        await(validateDependencies(res0))

        assert(
          res1.projectCache0.contains(
            (
              mod"io.get-coursier:coursier-cli_2.12",
              VersionConstraint("1.1.0-M8")
            )
          )
        )
        assert(
          res1.projectCache0.contains(
            (
              mod"io.get-coursier:coursier-cli_2.12",
              VersionConstraint("2.0.0-RC6-16")
            )
          )
        )
        assert {
          res1.finalDependenciesCache.keys.exists(dep =>
            dep.module == mod"io.get-coursier:coursier-cli_2.12" && dep.versionConstraint.asString == "1.1.0-M8"
          )
        }
        assert {
          res1.finalDependenciesCache.keys.exists(dep =>
            dep.module == mod"io.get-coursier:coursier-cli_2.12" && dep.versionConstraint.asString == "2.0.0-RC6-16"
          )
        }
      }
    }

    test("source artifact type if sources classifier") {
      async {
        val dep = dep"org.apache.commons:commons-compress:1.5,classifier=sources"
        assert(dep.publication.classifier == Classifier("sources"))

        val res = await {
          resolve
            .addDependencies(dep)
            .future()
        }

        await(validateDependencies(res))

        val depArtifacts = res.dependencyArtifacts0(Some(Seq(Classifier.sources)))

        val urls = depArtifacts.map(_._3.url).toSet
        val expectedUrls = Set(
          "https://repo1.maven.org/maven2/org/tukaani/xz/1.2/xz-1.2-sources.jar",
          "https://repo1.maven.org/maven2/org/apache/commons/commons-compress/1.5/commons-compress-1.5-sources.jar"
        )
        assert(urls == expectedUrls)

        val pubTypes         = depArtifacts.collect { case (_, Right(pub), _) => pub.`type` }.toSet
        val expectedPubTypes = Set(Type.source)
        assert(pubTypes == expectedPubTypes)
      }
    }

    test("user-supplied artifact type") {
      async {
        val dep =
          dep"io.grpc:protoc-gen-grpc-java:1.23.0,classifier=linux-x86_64,ext=exe,type=protoc-plugin"
        assert(dep.publication.`type` == Type("protoc-plugin"))
        assert(dep.publication.ext == Extension("exe"))
        assert(dep.publication.classifier == Classifier("linux-x86_64"))

        val res = await {
          resolve
            .addDependencies(dep)
            .future()
        }

        await(validateDependencies(res))

        val depArtifacts = res.dependencyArtifacts0()
        assert(depArtifacts.lengthCompare(1) == 0)

        val (_, pub0, artifact) = depArtifacts.head

        val expectedUrl =
          "https://repo1.maven.org/maven2/io/grpc/protoc-gen-grpc-java/1.23.0/protoc-gen-grpc-java-1.23.0-linux-x86_64.exe"
        assert(artifact.url == expectedUrl)

        assert(pub0.isRight)
        val pub = pub0.toOption.get
        assert(pub.`type` == Type("protoc-plugin"))
        assert(pub.ext == Extension("exe"))
        assert(pub.classifier == Classifier("linux-x86_64"))
      }
    }

    test("new line in properties") {
      async {
        val res = await {
          resolve
            .addDependencies(
              dep"org.kie:kie-api:7.27.0.Final",
              dep"org.kie.server:kie-server-api:7.27.0.Final",
              dep"org.kie.server:kie-server-client:7.27.0.Final"
            )
            .future()
        }

        await(validateDependencies(res))
      }
    }

    test("default value for pom project_packaging property") {
      async {
        val dep = dep"org.nd4j:nd4j-native-platform:1.0.0-beta4"
        val params = ResolutionParams()
          .withOsInfo {
            Activation.Os(
              Some("x86_64"),
              Set("mac", "unix"),
              Some("mac os x"),
              Some("10.15.1")
            )
          }
          .withJdkVersion(Version("1.8.0_121"))
        val res = await {
          resolve
            .withResolutionParams(params)
            .addDependencies(dep)
            .future()
        }

        await(validateDependencies(res, params))

        val urls = res.dependencyArtifacts0().map(_._3.url)
        val wrongUrls =
          urls.filter(url => url.contains("$") || url.contains("{") || url.contains("}"))

        assert(urls.nonEmpty)
        assert(wrongUrls.isEmpty)
      }
    }

    test("pom project.packaging property") {
      async {
        val dep = dep"org.apache.zookeeper:zookeeper:3.5.0-alpha"
        val res = await {
          resolve
            .addDependencies(dep)
            .future()
        }

        await(validateDependencies(res))

        // The one we're interested in here
        val pomUrl =
          "https://repo1.maven.org/maven2/org/apache/zookeeper/zookeeper/3.5.0-alpha/zookeeper-3.5.0-alpha.pom"
        val urls = res.dependencyArtifacts0().map(_._3.url).toSet

        assert(urls.contains(pomUrl))
      }
    }

    test("child property substitution in parent POM") {
      async {
        val deps = Seq(
          dep"org.bytedeco:mkl-platform:2019.5-1.5.2",
          dep"org.bytedeco:mkl-platform-redist:2019.5-1.5.2"
        )
        val res = await {
          resolve
            .addDependencies(deps: _*)
            .future()
        }

        await(validateDependencies(res))

        val urls = res.dependencyArtifacts0().map(_._3.url).toSet
        val expectedUrls = Set(
          "https://repo1.maven.org/maven2/org/bytedeco/javacpp/1.5.2/javacpp-1.5.2.jar",
          "https://repo1.maven.org/maven2/org/bytedeco/mkl-platform-redist/2019.5-1.5.2/mkl-platform-redist-2019.5-1.5.2.jar",
          "https://repo1.maven.org/maven2/org/bytedeco/mkl-platform/2019.5-1.5.2/mkl-platform-2019.5-1.5.2.jar",
          "https://repo1.maven.org/maven2/org/bytedeco/mkl/2019.5-1.5.2/mkl-2019.5-1.5.2-linux-x86-redist.jar",
          "https://repo1.maven.org/maven2/org/bytedeco/mkl/2019.5-1.5.2/mkl-2019.5-1.5.2-linux-x86.jar",
          "https://repo1.maven.org/maven2/org/bytedeco/mkl/2019.5-1.5.2/mkl-2019.5-1.5.2-linux-x86_64-redist.jar",
          "https://repo1.maven.org/maven2/org/bytedeco/mkl/2019.5-1.5.2/mkl-2019.5-1.5.2-linux-x86_64.jar",
          "https://repo1.maven.org/maven2/org/bytedeco/mkl/2019.5-1.5.2/mkl-2019.5-1.5.2-macosx-x86_64-redist.jar",
          "https://repo1.maven.org/maven2/org/bytedeco/mkl/2019.5-1.5.2/mkl-2019.5-1.5.2-macosx-x86_64.jar",
          "https://repo1.maven.org/maven2/org/bytedeco/mkl/2019.5-1.5.2/mkl-2019.5-1.5.2-windows-x86-redist.jar",
          "https://repo1.maven.org/maven2/org/bytedeco/mkl/2019.5-1.5.2/mkl-2019.5-1.5.2-windows-x86.jar",
          "https://repo1.maven.org/maven2/org/bytedeco/mkl/2019.5-1.5.2/mkl-2019.5-1.5.2-windows-x86_64-redist.jar",
          "https://repo1.maven.org/maven2/org/bytedeco/mkl/2019.5-1.5.2/mkl-2019.5-1.5.2-windows-x86_64.jar",
          "https://repo1.maven.org/maven2/org/bytedeco/mkl/2019.5-1.5.2/mkl-2019.5-1.5.2.jar"
        )

        assert(urls == expectedUrls)
      }
    }

    test("Artifacts with classifier are non optional") {
      async {
        val dep = dep"io.netty:netty-transport-native-epoll:4.1.44.Final,classifier=woops"
        val res = await {
          resolve.addDependencies(dep).future()
        }

        await(validateDependencies(res))

        val artifacts = res.dependencyArtifacts0()
        val expectedUrl =
          "https://repo1.maven.org/maven2/io/netty/netty-transport-native-epoll/4.1.44.Final/netty-transport-native-epoll-4.1.44.Final-woops.jar"
        val (_, _, woopsArtifact) = artifacts.find(_._3.url == expectedUrl).getOrElse {
          sys.error(s"Expected artifact with URL $expectedUrl")
        }
        assert(!woopsArtifact.optional)
      }
    }

    test("JDK profile activation") {
      val dep = dep"com.helger:ph-jaxb-pom:1.0.3"
      test("JDK 1.8") {
        async {
          val params = resolve.resolutionParams.withJdkVersion(Version("1.8.0_121"))
          val res = await {
            resolve
              .withResolutionParams(params)
              .addDependencies(dep)
              .future()
          }
          await(validateDependencies(res, params))
        }
      }
      test("JDK 11") {
        async {
          val params = resolve.resolutionParams.withJdkVersion(Version("11.0.5"))
          val res = await {
            resolve
              .withResolutionParams(params)
              .addDependencies(dep)
              .future()
          }
          await(validateDependencies(res, params))
        }
      }
    }

    test("parent properties of import scope dependency") {
      async {
        val res = await {
          resolve
            .addDependencies(dep"com.yahoo.athenz:athenz-zts-java-client-core:1.10.7")
            .future()
        }
        await(validateDependencies(res))
      }
    }

    test("profile activation with missing property") {
      async {
        val params = ResolutionParams()
          .withOsInfo {
            Activation.Os(
              Some("x86_64"),
              Set("mac", "unix"),
              Some("mac os x"),
              Some("10.15.1")
            )
          }
          .withJdkVersion(Version("1.8.0_121"))
        val res = await {
          resolve
            .withResolutionParams(params)
            .addDependencies(dep"org.openjfx:javafx-base:18-ea+2")
            .future()
        }
        await(validateDependencies(res, params))

        val artifacts = res.artifacts()
        val urls      = artifacts.map(_.url)
        val expectedUrls = Seq(
          "https://repo1.maven.org/maven2/org/openjfx/javafx-base/18-ea+2/javafx-base-18-ea+2.jar",
          "https://repo1.maven.org/maven2/org/openjfx/javafx-base/18-ea+2/javafx-base-18-ea+2-mac.jar"
        )
        assert(urls == expectedUrls)
      }
    }

    test("keep provided dependencies") {
      async {

        val resolve0 = resolve
          .addDependencies(dep"com.lihaoyi:pprint_2.12:0.5.4")
          .mapResolutionParams(_.withKeepProvidedDependencies(true))

        val res = await(resolve0.future())

        await(validateDependencies(res, resolve0.resolutionParams))
      }
    }

    test("mapDependencies") {
      async {

        val resolve0 = resolve
          .addDependencies(dep"com.lihaoyi:pprint_2.12:0.5.4")
          .withMapDependenciesOpt(
            Some { dep =>
              if (dep.module.name.value == "scala-library")
                dep.withVersionConstraint(VersionConstraint("2.12.12"))
              else
                dep
            }
          )

        val res = await(resolve0.future())

        await {
          validateDependencies(
            res,
            resolve0.resolutionParams,
            extraKeyPart = "customMapDependencies"
          )
        }
      }
    }

    test("spark") {

      def check(
        sparkVersion: String,
        scalaBinaryVersion: String,
        profiles: Set[String] = Set.empty,
        forceDepMgmtVersions: Option[Boolean] = None
      ): Future[Unit] =
        async {
          val params = ResolutionParams()
            .withJdkVersion(Version("8.0"))
            .withProfiles(profiles)
            .withForceDepMgmtVersions(forceDepMgmtVersions)
          val res = await {
            resolve
              .addDependencies(
                Dependency(
                  Module(
                    org"org.apache.spark",
                    ModuleName(s"spark-core_$scalaBinaryVersion"),
                    Map.empty
                  ),
                  VersionConstraint(sparkVersion)
                )
              )
              .withResolutionParams(params)
              .future()
          }
          // await(validateDependencies(res))
          val found       = res.orderedDependencies.map(_.moduleVersionConstraint).toMap
          val scalaLibOpt = found.get(mod"org.scala-lang:scala-library").map(_.asString)
          assert(scalaLibOpt.exists(_.startsWith(s"$scalaBinaryVersion.")))
          // !
          await(validateDependencies(res, params))
        }

      test("scala 2_10") {
        test("spark 1_2_1") {
          check("1.2.1", "2.10", forceDepMgmtVersions = Some(true))
        }
        test("spark 1_6_3") {
          check("1.6.3", "2.10")
        }
        test("spark 2_1_0") {
          check("2.1.0", "2.10", profiles = Set("scala-2.10", "!scala-2.11"))
        }
        test("spark 2_2_3") {
          check("2.2.3", "2.10", profiles = Set("scala-2.10", "!scala-2.11"))
        }
      }

      test("scala 2_11") {
        test("spark 1_2_1") {
          check(
            "1.2.1",
            "2.11",
            forceDepMgmtVersions = Some(true),
            profiles = Set("!scala-2.10", "scala-2.11")
          )
        }
        test("spark 1_6_3") {
          check("1.6.3", "2.11", profiles = Set("!scala-2.10", "scala-2.11"))
        }
        test("spark 2_1_0") {
          check("2.1.0", "2.11")
        }
        test("spark 2_2_3") {
          check("2.2.3", "2.11")
        }

        test("spark 2_3_4") {
          check("2.3.4", "2.11")
        }

        test("spark 2_4_8") {
          check("2.4.8", "2.11")
        }
      }

      test("scala 2_12") {
        test("spark 2_4_8") {
          check("2.4.8", "2.12")
        }

        test("spark 3_1_3") {
          check("3.1.3", "2.12")
        }

        test("spark 3_2_4") {
          check("3.2.4", "2.12")
        }
        test("spark 3_5_3") {
          check("3.5.3", "2.12")
        }
      }

      test("scala 2_13") {
        test("spark 3_2_4") {
          check("3.2.4", "2.13")
        }
        test("spark 3_5_3") {
          check("3.5.3", "2.13")
        }

        test("spark 4.0.0-preview2") {
          check("4.0.0-preview2", "2.13")
        }
      }
    }

    test("spring") {
      test("data-rest") {
        check(dep"org.springframework.boot:spring-boot-starter-data-rest:3.3.4")
      }
      test("graphql") {
        check(dep"org.springframework.boot:spring-boot-starter-graphql:3.3.4")
      }
      test("integration") {
        check(dep"org.springframework.boot:spring-boot-starter-integration:3.3.4")
      }
      test("oauth2-client") {
        check(dep"org.springframework.boot:spring-boot-starter-oauth2-client:3.3.4")
      }
      test("web") {
        check(dep"org.springframework.boot:spring-boot-starter-web:3.3.4")
      }
      test("web-services") {
        check(dep"org.springframework.boot:spring-boot-starter-web-services:3.3.4")
      }
      test("webflux") {
        check(dep"org.springframework.boot:spring-boot-starter-webflux:3.3.4")
      }
      test("security-test") {
        check(dep"org.springframework.security:spring-security-test:6.3.4")
      }
    }

    test("quarkus") {
      test("rest") {
        doCheck(
          resolve.withResolutionParams(
            resolve.resolutionParams.withOsInfo(
              Activation.Os(Some("x86_64"), Set("mac", "unix"), Some("mac os x"), Some("10.15.1"))
            )
          ),
          Seq(dep"io.quarkus:quarkus-rest:3.15.1")
        )
      }
      test("rest-jackson") {
        doCheck(
          resolve.withResolutionParams(
            resolve.resolutionParams.withOsInfo(
              Activation.Os(Some("x86_64"), Set("mac", "unix"), Some("mac os x"), Some("10.15.1"))
            )
          ),
          Seq(dep"io.quarkus:quarkus-rest-jackson:3.15.1")
        )
      }
      test("hibernate-orm-panache") {
        check(dep"io.quarkus:quarkus-hibernate-orm-panache:3.15.1")
      }
      test("jdbc-postgresql") {
        check(dep"io.quarkus:quarkus-jdbc-postgresql:3.15.1")
      }
      test("arc") {
        check(dep"io.quarkus:quarkus-arc:3.15.1")
      }
      test("hibernate-orm") {
        check(dep"io.quarkus:quarkus-hibernate-orm:3.15.1")
      }
      test("junit5") {
        check(dep"io.quarkus:quarkus-junit5:3.15.1")
      }
      test("rest-assured") {
        check(dep"io.rest-assured:rest-assured:5.5.0")
      }
    }

    test("android") {

      def androidCheck(dependencies: Dependency*): Future[Unit] =
        async {
          val res = await {
            resolve
              .addRepositories(Repositories.google)
              .addDependencies(dependencies: _*)
              .future()
          }
          await(validateDependencies(res))
        }

      test("activity") {
        androidCheck(dep"androidx.activity:activity:1.8.2")
      }
      test("activity-compose") {
        androidCheck(dep"androidx.activity:activity-compose:1.8.2")
      }
      test("runtime") {
        androidCheck(dep"androidx.compose.runtime:runtime:1.3.1")
      }
      test("material3") {
        androidCheck(dep"androidx.compose.material3:material3:1.0.1")
      }
    }

    test("bom") {

      test("spark-parent") {
        test {
          bomCheck(dep"org.apache.spark:spark-parent_2.13:3.5.3".asBomDependency)(
            dep"org.apache.commons:commons-lang3"
          )
        }
        test {
          bomCheck(dep"org.apache.spark:spark-parent_2.13:3.5.3".asBomDependency)(
            dep"org.glassfish.jaxb:jaxb-runtime"
          )
        }
        test {
          bomCheck(dep"org.apache.spark:spark-parent_2.13:3.5.3".asBomDependency)(
            dep"org.apache.logging.log4j:log4j-core"
          )
        }
      }

      test("quarkus-bom") {
        test("disabled") {
          check(dep"ch.epfl.scala:bsp4j:2.2.0-M2")
        }
        test("enabled") {
          bomCheck(dep"io.quarkus:quarkus-bom:3.16.2".asBomDependency)(
            dep"ch.epfl.scala:bsp4j:2.2.0-M2"
          )
        }
      }

      // this one pulls other BOMs via dependency imports, and
      // these tend to use the `project.version` Java property fairly often,
      // so this checks that this property is substituted at the right time
      test("google-cloud-bom") {
        test("protobuf-java") {
          bomCheck(dep"com.google.cloud:libraries-bom:26.50.0".asBomDependency)(
            dep"com.google.protobuf:protobuf-java"
          )
        }

        test("scalapbc") {
          test("no-bom") {
            check(dep"com.thesamet.scalapb:scalapbc_2.13:0.9.8")
          }
          test("bom") {
            bomCheck(dep"com.google.cloud:libraries-bom:26.50.0".asBomDependency)(
              dep"com.thesamet.scalapb:scalapbc_2.13:0.9.8"
            )
          }
          test("bom-via-dep") {
            // BOM should bump protobuf-java to 4.28.3
            check(
              dep"com.thesamet.scalapb:scalapbc_2.13:0.9.8"
                .addBom(mod"com.google.cloud:libraries-bom", VersionConstraint("26.50.0"))
            )
          }
        }
      }

      test("scalatest-play") {
        test("default") {
          bomCheck(dep"org.apache.spark:spark-parent_2.13:3.5.3".asBomDependency)(
            dep"org.scalatestplus.play:scalatestplus-play_2.13:7.0.1"
          )
        }
        test("test") {
          // BOM should override org.scalatest:scalatest_2.13 version,
          // as we pull the test entries too
          bomCheck(
            dep"org.apache.spark:spark-parent_2.13:3.5.3"
              .withVariantSelector(VariantSelector.ConfigurationBased(Configuration.test))
              .asBomDependency
          )(
            dep"org.scalatestplus.play:scalatestplus-play_2.13:7.0.1"
          )
        }
        test("testViaBomDep") {
          test {
            // BOM should force org.scalatest:scalatest_2.13 version to 3.2.16
            // (because we take into account dep mgmt with test scope here)
            check(
              dep"org.scalatestplus.play:scalatestplus-play_2.13:7.0.1".addBom(
                dep"org.apache.spark:spark-parent_2.13:3.5.3"
                  .withVariantSelector(VariantSelector.ConfigurationBased(Configuration.test))
                  .asBomDependency
              )
            )
          }
          test {
            // BOM shouldn't force org.scalatest:scalatest_2.13 version to 3.2.16,
            // leaving it to 3.2.17
            // (because we don't take into account dep mgmt with test scope here)
            check(
              dep"org.scalatestplus.play:scalatestplus-play_2.13:7.0.1".addBom(
                dep"org.apache.spark:spark-parent_2.13:3.5.3"
                  .withVariantSelector(
                    VariantSelector.ConfigurationBased(Configuration.defaultRuntime)
                  )
                  .asBomDependency
              )
            )
          }
        }
      }

      test("runtime") {
        bomCheck(
          dep"io.quarkus:quarkus-bom:3.15.1"
            .withVariantSelector(VariantSelector.ConfigurationBased(Configuration.runtime))
            .asBomDependency
        )(
          dep"org.mvnpm.at.hpcc-js:wasm"
        )
      }

      test("provided") {
        test {
          // BOM should fill the version, even though
          // protobuf-java is marked as provided there
          bomCheck(
            dep"org.apache.spark:spark-parent_2.13:3.5.3"
              .withVariantSelector(VariantSelector.ConfigurationBased(Configuration.provided))
              .asBomDependency
          )(
            dep"com.google.protobuf:protobuf-java"
          )
        }
        test {
          // BOM should fill the version, even though
          // protobuf-java is marked as provided there
          bomCheck(
            dep"org.apache.spark:spark-parent_2.13:3.5.3"
              .withVariantSelector(VariantSelector.ConfigurationBased(Configuration.provided))
              .asBomDependency
          )(
            dep"com.google.protobuf:protobuf-java-util"
          )
        }
        // test("check") {
        //   async {
        //
        //     val params = ResolutionParams()
        //       .withOsInfo {
        //         Activation.Os(
        //           Some("x86_64"),
        //           Set("mac", "unix"),
        //           Some("mac os x"),
        //           Some("10.15.1")
        //         )
        //       }
        //       .withJdkVersion(Version("1.8.0_121"))
        //
        //     val res = await {
        //       resolve
        //         .withResolutionParams(params)
        //         .addDependencies(dep"com.google.protobuf:protobuf-java")
        //         .addBom(
        //           dep"org.apache.spark:spark-parent_2.13:3.5.3"
        //             .withVariantSelector(VariantSelector.ConfigurationBased(Configuration.compile))
        //             .asBomDependency
        //         )
        //         .io
        //         .attempt
        //         .future()
        //     }
        //
        //     val isLeft = res.isLeft
        //     assert(isLeft)
        //
        //     val error = res.swap.toOption.get
        //
        //     error match {
        //       case e: ResolutionError.CantDownloadModule =>
        //         assert(e.module == mod"com.google.protobuf:protobuf-java")
        //       case _ =>
        //         throw error
        //     }
        //   }
        // }

        test("bom-dep") {
          test {
            // BOM should have no effect, no empty version to fill
            // and no transitive dependency
            check(
              dep"com.google.protobuf:protobuf-java:3.7.1".addBom(
                dep"org.apache.spark:spark-parent_2.13:3.5.3"
                  .withVariantSelector(VariantSelector.ConfigurationBased(Configuration.provided))
                  .asBomDependency
              )
            )
          }
          test {
            // BOM should override transitive dependency versions,
            // com.google.protobuf:protobuf-java in particular, but not
            // com.google.protobuf:protobuf-java-util
            check(
              dep"com.google.protobuf:protobuf-java-util:3.7.1".addBom(
                dep"org.apache.spark:spark-parent_2.13:3.5.3"
                  .withVariantSelector(VariantSelector.ConfigurationBased(Configuration.provided))
                  .asBomDependency
              )
            )
          }
          test("check") {
            // BOM shouldn't override com.google.protobuf:protobuf-java
            // version, as it's marked as provided there
            check(
              dep"com.google.protobuf:protobuf-java-util:3.7.1".addBom(
                dep"org.apache.spark:spark-parent_2.13:3.5.3"
                  .withVariantSelector(
                    VariantSelector.ConfigurationBased(Configuration.defaultRuntime)
                  )
                  .asBomDependency
              )
            )
          }
        }

        test("bom-dep-force-ver") {
          test {
            // BOM should override root dependency version (forceOverrideVersions is true)
            // and transitive dependencies' versions
            check(
              dep"com.google.protobuf:protobuf-java:3.7.1".addBom(
                dep"org.apache.spark:spark-parent_2.13:3.5.3"
                  .withVariantSelector(VariantSelector.ConfigurationBased(Configuration.provided))
                  .asBomDependency
                  .withForceOverrideVersions(true)
              )
            )
          }
          test {
            // BOM should override root dependency version (forceOverrideVersions is true)
            // and transitive dependencies' versions
            check(
              dep"com.google.protobuf:protobuf-java-util:3.7.1".addBom(
                dep"org.apache.spark:spark-parent_2.13:3.5.3"
                  .withVariantSelector(VariantSelector.ConfigurationBased(Configuration.provided))
                  .asBomDependency
                  .withForceOverrideVersions(true)
              )
            )
          }
          test("check") {
            // BOM shouldn't override root dependency version (even though
            // forceOverrideVersions is true) or transitive dep
            // com.google.protobuf:protobuf-java version, as these are marked
            // as provided in the BOM, and we don't ask for this config here
            check(
              dep"com.google.protobuf:protobuf-java-util:3.7.1".addBom(
                dep"org.apache.spark:spark-parent_2.13:3.5.3"
                  .asBomDependency
                  .withForceOverrideVersions(true)
              )
            )
          }
        }
      }

      test("bom-dep") {
        test {
          // The BOM shouldn't apply to scalapbc in that case,
          // protobuf-java should stay on 3.7.1
          check(
            dep"com.thesamet.scalapb:scalapbc_2.13:0.9.8",
            dep"com.lihaoyi:pprint_2.13:0.9.3"
              .addBom(mod"com.google.cloud:libraries-bom", VersionConstraint("26.50.0"))
          )
        }
        test {
          // The BOM should apply to scalapbc in that case,
          // protobuf-java should switch to 4.28.3
          check(
            dep"com.thesamet.scalapb:scalapbc_2.13:0.9.8"
              .addBom(mod"com.google.cloud:libraries-bom", VersionConstraint("26.50.0")),
            dep"com.lihaoyi:pprint_2.13:0.9.3"
          )
        }
      }

      test("precedence") {
        test("bomOverride") {
          test {
            // protobuf-java 4.28.1 (override)
            // protobuf-java-util 4.28.3 (input version)
            check(
              dep"com.google.protobuf:protobuf-java-util:4.28.3,bom=com.google.protobuf%protobuf-bom%4.28.0,override=com.google.protobuf%protobuf-java%4.28.1"
            )
          }
          test {
            // protobuf-java 4.28.3 (override)
            // protobuf-java-util 4.28.0 (input version)
            check(
              dep"com.google.protobuf:protobuf-java-util:4.28.0,bom=com.google.protobuf%protobuf-bom%4.28.1,override=com.google.protobuf%protobuf-java%4.28.3"
            )
          }
          test {
            // protobuf-java 4.28.0 (override)
            // protobuf-java-util 4.28.1 (input version)
            check(
              dep"com.google.protobuf:protobuf-java-util:4.28.1,bom=com.google.protobuf%protobuf-bom%4.28.3,override=com.google.protobuf%protobuf-java%4.28.0"
            )
          }
        }

        test("bomBom") {
          test {
            // protobuf-java 4.28.1 (first bom)
            // protobuf-java-util 4.28.3 (input version)
            check(
              dep"com.google.protobuf:protobuf-java-util:4.28.3,bom=com.google.protobuf%protobuf-bom%4.28.1,bom=com.google.protobuf%protobuf-bom%4.28.0"
            )
          }
          test {
            // protobuf-java 4.28.3 (first bom)
            // protobuf-java-util 4.28.0 (input version)
            check(
              dep"com.google.protobuf:protobuf-java-util:4.28.0,bom=com.google.protobuf%protobuf-bom%4.28.3,bom=com.google.protobuf%protobuf-bom%4.28.1"
            )
          }
          test {
            // protobuf-java 4.28.0 (first bom)
            // protobuf-java-util 4.28.1 (input version)
            check(
              dep"com.google.protobuf:protobuf-java-util:4.28.1,bom=com.google.protobuf%protobuf-bom%4.28.0,bom=com.google.protobuf%protobuf-bom%4.28.3"
            )
          }
        }

        test("overrideOverride") {
          test {
            // protobuf-java 4.28.1 (first override)
            // protobuf-java-util 4.28.3 (input version)
            check(
              dep"com.google.protobuf:protobuf-java-util:4.28.3,override=com.google.protobuf%protobuf-java%4.28.1,override=com.google.protobuf%protobuf-java%4.28.0"
            )
          }
          test {
            // protobuf-java 4.28.3 (first override)
            // protobuf-java-util 4.28.0 (input version)
            check(
              dep"com.google.protobuf:protobuf-java-util:4.28.0,override=com.google.protobuf%protobuf-java%4.28.3,override=com.google.protobuf%protobuf-java%4.28.1"
            )
          }
          test {
            // protobuf-java 4.28.0 (first override)
            // protobuf-java-util 4.28.1 (input version)
            check(
              dep"com.google.protobuf:protobuf-java-util:4.28.1,override=com.google.protobuf%protobuf-java%4.28.0,override=com.google.protobuf%protobuf-java%4.28.3"
            )
          }
        }
      }
    }

    test("delayed-properties") {
      check(dep"org.apache.logging.log4j:log4j-slf4j-impl:2.22.0")
    }

    test("scalatest-play") {
      check(dep"org.scalatestplus.play:scalatestplus-play_2.13:7.0.1")
    }

    test("scope") {
      test("compile") {
        scopeCheck(Configuration.compile, Seq(Repositories.google))(
          dep"androidx.compose.animation:animation-core:1.1.1",
          dep"androidx.compose.ui:ui:1.1.1"
        )
      }

      test("defaultCompile") {
        scopeCheck(Configuration.defaultCompile, Seq(Repositories.google))(
          dep"androidx.compose.animation:animation-core:1.1.1",
          dep"androidx.compose.ui:ui:1.1.1"
        )
      }
    }

    test("large resolution") {
      check(dep"io.trino:trino-hive:467")
    }

    test("dep import and parent precedence") {
      // bom has a dep import of a BOM pulling protobuf-java 3.x
      // and has parents pulling protobuf-java 4.x
      // The former should have precedence over the latter
      bomCheck(dep"com.google.cloud:libraries-bom-protobuf3:26.51.0".asBomDependency)(
        dep"com.google.protobuf:protobuf-java:"
      )
    }

    test("gradle modules") {
      test("kotlinx-html-js") {
        test("no-support") {
          check(
            dep"org.jetbrains.kotlinx:kotlinx-html-js:0.11.0"
          )
        }
        test("support") {
          test("variants") {
            test("dependency") {
              gradleModuleCheck(
                dep"org.jetbrains.kotlinx:kotlinx-html-js:0.11.0,variant.org.gradle.usage=kotlin-runtime,variant.org.jetbrains.kotlin.platform.type=js,variant.org.jetbrains.kotlin.js.compiler=ir,variant.org.gradle.category=library"
              )
            }
            test("global") {
              gradleModuleCheck0(
                defaultAttributes = Some(
                  VariantSelector.AttributesBased(Map(
                    "org.gradle.usage" -> VariantMatcher.Runtime,
                    "org.jetbrains.kotlin.platform.type" ->
                      VariantMatcher.Equals("js"),
                    "org.jetbrains.kotlin.js.compiler" ->
                      VariantMatcher.Equals("ir"),
                    "org.gradle.category" -> VariantMatcher.Library
                  ))
                )
              )(
                dep"org.jetbrains.kotlinx:kotlinx-html-js:0.11.0"
              )
            }
          }
          test("missing variants") {
            async {
              val res = await {
                enableModules(resolve)
                  .addDependencies(
                    dep"org.jetbrains.kotlinx:kotlinx-html-js:0.11.0,variant.org.gradle.usage=kotlin-runtime"
                  )
                  .futureEither()
              }
              assert(res.isLeft)
              assert(
                res.left.toOption.get.getMessage
                  .contains(
                    "Found too many variants in org.jetbrains.kotlin:kotlin-stdlib:1.9.22 for"
                  )
              )
            }
          }
        }
      }
      test("android") {
        test("dependency") {
          def withVariant(dep: Dependency, map: Map[String, VariantMatcher]) =
            dep.withVariantSelector(VariantSelector.AttributesBased(map))

          def testVariants(
            config: Configuration,
            map: Map[String, VariantMatcher]
          ): Future[Unit] =
            gradleModuleCheck0(defaultConfiguration = Some(config))(
              withVariant(dep"androidx.core:core-ktx:1.15.0", map),
              withVariant(dep"androidx.activity:activity-compose:1.9.3", map),
              withVariant(dep"androidx.compose.ui:ui:1.7.5", map),
              withVariant(dep"androidx.compose.material3:material3:1.3.1", map)
            )

          // needs some extra default attributes for compile scope to work

          test("runtime") {
            testVariants(
              Configuration.runtime,
              Map(
                "org.gradle.usage"                   -> VariantMatcher.Equals("java-runtime"),
                "org.gradle.category"                -> VariantMatcher.Equals("library"),
                "org.jetbrains.kotlin.platform.type" -> VariantMatcher.Equals("jvm")
              )
            )
          }
        }
        test("global") {
          def testVariants(
            config: Configuration,
            map: Map[String, VariantMatcher]
          ): Future[Unit] =
            gradleModuleCheck0(
              defaultConfiguration = Some(config),
              defaultAttributes = Some(VariantSelector.AttributesBased(map))
            )(
              dep"androidx.core:core-ktx:1.15.0",
              dep"androidx.activity:activity-compose:1.9.3",
              dep"androidx.compose.ui:ui:1.7.5",
              dep"androidx.compose.material3:material3:1.3.1"
            )

          test("compile") {
            testVariants(
              Configuration.compile,
              Map(
                "org.gradle.usage"                   -> VariantMatcher.Equals("java-api"),
                "org.gradle.category"                -> VariantMatcher.Equals("library"),
                "org.jetbrains.kotlin.platform.type" -> VariantMatcher.Equals("jvm")
              )
            )
          }
          test("runtime") {
            testVariants(
              Configuration.runtime,
              Map(
                "org.gradle.usage"                   -> VariantMatcher.Equals("java-runtime"),
                "org.gradle.category"                -> VariantMatcher.Equals("library"),
                "org.jetbrains.kotlin.platform.type" -> VariantMatcher.Equals("jvm")
              )
            )
          }
        }
      }
      test("fallback from config") {
        test("android") {
          test("compile") {
            val attr = VariantSelector.AttributesBased().withMatchers(
              Map(
                "org.jetbrains.kotlin.platform.type" -> VariantMatcher.Equals("jvm")
              )
            )
            gradleModuleCheck0(
              defaultConfiguration = Some(Configuration.compile),
              defaultAttributes = Some(attr)
            )(
              dep"androidx.core:core-ktx:1.15.0:compile"
            )
          }
          test("runtime") {
            val attr = VariantSelector.AttributesBased().withMatchers(
              Map(
                "org.jetbrains.kotlin.platform.type" -> VariantMatcher.Equals("jvm")
              )
            )
            gradleModuleCheck0(defaultAttributes = Some(attr))(
              dep"androidx.core:core-ktx:1.15.0"
            )
          }
        }
        test("kotlin") {
          test("runtime") {
            test("js") {
              val attr = VariantSelector.AttributesBased().withMatchers(
                Map(
                  "org.jetbrains.kotlin.platform.type" ->
                    VariantMatcher.Equals("js"),
                  "org.jetbrains.kotlin.js.compiler" -> VariantMatcher.Equals("ir")
                )
              )
              gradleModuleCheck0(defaultAttributes = Some(attr))(
                dep"org.jetbrains.kotlinx:kotlinx-html-js:0.11.0"
              )
            }
            test("jvm") {
              val attr = VariantSelector.AttributesBased().withMatchers(
                Map(
                  "org.gradle.jvm.environment" ->
                    VariantMatcher.Equals("standard-jvm")
                )
              )
              gradleModuleCheck0(defaultAttributes = Some(attr))(
                dep"org.jetbrains.kotlinx:kotlinx-html-js:0.11.0"
              )
            }
          }
        }
      }
      test("module-bom") {
        val resolve0 = resolve.withResolutionParams(
          resolve.resolutionParams.withOsInfo(
            Activation.Os(Some("x86_64"), Set("mac", "unix"), Some("mac os x"), Some("10.15.1"))
          )
        )
        gradleModuleCheck0(resolve0 = resolve0)(
          dep"io.quarkus:quarkus-rest-jackson:3.15.1"
        )
      }
      test("quarkus-junit5") {
        val resolve0 = resolve.addVariantAttributes(
          "org.gradle.jvm.environment" -> VariantMatcher.Equals("standard-jvm")
        )
        gradleModuleCheck0(resolve0 = resolve0)(
          dep"io.quarkus:quarkus-junit5:3.15.1"
        )
      }
      test("quarkus-rest-assured") {
        gradleModuleCheck(dep"io.rest-assured:rest-assured:5.5.0")
      }

      test("scalatest-play") {
        val resolve0 = resolve
          .addVariantAttributes(
            "org.gradle.jvm.environment"     -> VariantMatcher.Equals("standard-jvm"),
            "org.gradle.dependency.bundling" -> VariantMatcher.Equals("external")
          )
          .addBomConfigs(
            dep"org.apache.spark:spark-parent_2.13:3.5.3".asBomDependency
          )
        gradleModuleCheck0(resolve0 = resolve0)(
          dep"org.scalatestplus.play:scalatestplus-play_2.13:7.0.1"
        )
      }
      test("bom") {
        gradleModuleCheck0(
          defaultAttributes = Some(
            VariantSelector.AttributesBased(Map(
              "org.gradle.jvm.environment" -> VariantMatcher.Equals("standard-jvm")
            ))
          )
        )(
          dep"org.junit-pioneer:junit-pioneer:1.9.1"
        )
      }
      test("only prefers") {
        gradleModuleCheck0(
          defaultAttributes = Some(
            VariantSelector.AttributesBased(Map(
              "org.gradle.category"                -> VariantMatcher.Library,
              "org.gradle.dependency.bundling"     -> VariantMatcher.Equals("external"),
              "org.gradle.jvm.environment"         -> VariantMatcher.Equals("non-jvm"),
              "org.gradle.usage"                   -> VariantMatcher.Runtime,
              "org.jetbrains.kotlin.js.compiler"   -> VariantMatcher.Equals("ir"),
              "org.jetbrains.kotlin.platform.type" -> VariantMatcher.Equals("js")
            ))
          ),
          defaultConfiguration = Some(Configuration.runtime),
          attributesBasedReprAsToString = true
        )(
          dep"io.kotest:kotest-framework-engine-js:6.0.0.M3"
        )
      }
      test("wrong module name") {
        gradleModuleCheck0(
          defaultAttributes = Some(
            VariantSelector.AttributesBased(Map(
              "org.gradle.category"        -> VariantMatcher.Library,
              "org.gradle.jvm.environment" -> VariantMatcher.Equals("standard-jvm")
            ))
          ),
          attributesBasedReprAsToString = true
        )(
          dep"org.jetbrains.kotlin:kotlin-test-junit:2.0.20"
        )
      }
      test("module BOM") {
        gradleModuleCheck(
          dep"org.springframework.data:spring-data-jpa:2.5.4"
        )
      }
      test("any of") {
        gradleModuleCheck0(
          defaultAttributes = Some(
            VariantSelector.AttributesBased(Map(
              "org.jetbrains.kotlin.platform.type" ->
                VariantMatcher.AnyOf(Seq(
                  VariantMatcher.Equals("androidJvm"),
                  VariantMatcher.Equals("jvm")
                ))
            ))
          ),
          attributesBasedReprAsToString = true
        )(
          dep"androidx.compose.material3:material3:1.3.1",
          dep"androidx.collection:collection-ktx:1.4.0",
          dep"androidx.lifecycle:lifecycle-process:2.8.3",
          dep"androidx.lifecycle:lifecycle-common-java8:2.8.3"
        )
      }

      test("endorseStrictVersions") {
        gradleModuleCheck0(
          defaultAttributes = Some(
            VariantSelector.AttributesBased(Map(
              "org.jetbrains.kotlin.platform.type" -> VariantMatcher.Equals("jvm")
            ))
          ),
          attributesBasedReprAsToString = true
        )(
          dep"androidx.test.ext:junit:1.2.1"
        )
      }

      test("bom config graph") {
        val resolve0 = resolve
          .addVariantAttributes(
            "org.gradle.jvm.environment" -> VariantMatcher.Equals("standard-jvm")
          )
          .addBom(
            dep"io.micronaut.platform:micronaut-platform:4.9.2".asBomDependency
          )
        test("micronaut-inject-kotlin") {
          gradleModuleCheck0(resolve0 = resolve0)(
            dep"io.micronaut:micronaut-inject-kotlin:"
          )
        }
        test("micronaut-openapi") {
          gradleModuleCheck0(resolve0 = resolve0)(
            dep"io.micronaut.openapi:micronaut-openapi:"
          )
        }
      }

      test("lottie") {
        val resolve0 = resolve
          .addVariantAttributes(
            "org.gradle.jvm.environment"         -> VariantMatcher.Equals("standard-jvm"),
            "org.jetbrains.kotlin.platform.type" -> VariantMatcher.Equals("jvm"),
            "ui"                                 -> VariantMatcher.Equals("android")
          )
          .addRepositories(
            Repositories.google,
            MavenRepository("https://maven.pkg.jetbrains.space/public/p/compose/dev")
          )
        gradleModuleCheck0(resolve0 = resolve0, attributesBasedReprAsToString = true)(
          dep"com.airbnb.android:lottie-compose:6.6.6"
        )
      }
    }

    test("empty version") {
      async {

        val res = await {
          resolve
            .addDependencies(
              dep"com.google.protobuf:protobuf-java"
            )
            .io
            .attempt
            .future()
        }

        val isLeft = res.isLeft
        assert(isLeft)

        val error = res.swap.toOption.get

        error match {
          case e: ResolutionError.CantDownloadModule =>
            assert(e.module == mod"com.google.protobuf:protobuf-java")
            val message = e.getMessage.toString
            assert(message.contains("Error downloading com.google.protobuf:protobuf-java:"))
          case _ =>
            throw error
        }
      }
    }
  }
}
