package coursier.tests

import coursier.{Repositories, Resolve}
import coursier.core.{
  Activation,
  Classifier,
  Configuration,
  Dependency,
  Extension,
  Module,
  ModuleName,
  Reconciliation,
  Resolution,
  Type
}
import coursier.error.ResolutionError
import coursier.ivy.IvyRepository
import coursier.params.{MavenMirror, Mirror, ResolutionParams, TreeMirror}
import coursier.util.ModuleMatchers
import coursier.util.StringInterpolators._
import utest._

import scala.async.Async.{async, await}
import scala.collection.compat._
import scala.concurrent.Future

object ResolveTests extends TestSuite {

  import TestHelpers.{
    ec,
    cache,
    dependenciesWithRetainedVersion,
    handmadeMetadataBase,
    validateDependencies,
    versionOf
  }

  private val resolve = Resolve()
    .noMirrors
    .withCache(cache)
    .withResolutionParams(
      ResolutionParams()
        .withOsInfo {
          Activation.Os(
            Some("x86_64"),
            Set("mac", "unix"),
            Some("mac os x"),
            Some("10.15.1")
          )
        }
        .withJdkVersion("1.8.0_121")
    )

  def check(dependencies: Dependency*): Future[Unit] =
    async {
      val res = await {
        resolve
          .addDependencies(dependencies: _*)
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
      async {

        val resolve0 = resolve
          .addDependencies(dep"com.lihaoyi:ammonite_2.12.8:1.6.3")
          .mapResolutionParams { params =>
            params
              .withScalaVersion("2.12.8")
              .addForceVersion(mod"com.lihaoyi:upickle_2.12" -> "0.7.0")
              .addForceVersion(mod"io.get-coursier:coursier_2.12" -> "1.1.0-M6")
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

            val found         = dependenciesWithRetainedVersion(res).map(_.moduleVersion).toMap
            val ammVersionOpt = found.get(mod"com.lihaoyi:ammonite_2.12.8")
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

            val found = dependenciesWithRetainedVersion(res).map(_.moduleVersion).toSet
            val expected = Set(
              mod"org.scala-lang:scala-library" -> "2.12.8",
              mod"test:a_2.12"                  -> "1.0.2-SNAPSHOT"
            )

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

            val found = dependenciesWithRetainedVersion(res).map(_.moduleVersion).toSet
            val expected = Set(
              mod"org.scala-lang:scala-library" -> "2.12.8",
              mod"test:a_2.12"                  -> "1.0.1"
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

          val found = dependenciesWithRetainedVersion(res).map(_.moduleVersion).toSet
          val expected = Set(
            mod"org.scala-lang:scala-library" -> "2.12.8",
            mod"test:b_2.12"                  -> "1.0.2+20190524-1"
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
          val res = await {
            resolve0
              .mapResolutionParams(_.addExclusions((org"io.argonaut", name"*")))
              .future()
          }

          val argonaut = res.minDependencies.filter(_.module.organization == org"io.argonaut")
          assert(argonaut.isEmpty)

          await(validateDependencies(res))
        }
      }

      test("no org") {
        async {

          val res = await {
            resolve
              .addDependencies(
                dep"com.netflix.karyon:karyon-eureka:1.0.28"
                  .withConfiguration(Configuration.defaultCompile)
              )
              .future()
          }

          await(validateDependencies(res))
        }
      }
    }

    test("beam") {
      async {

        val res = await {
          resolve
            .addDependencies(dep"org.apache.beam:beam-sdks-java-io-google-cloud-platform:2.3.0")
            .future()
        }

        await(validateDependencies(res))
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

          val urls = res.dependencyArtifacts().map(_._3.url).toSet
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

          val found = dependenciesWithRetainedVersion(res).map(_.moduleVersion).toSet
          val expected = Set(
            mod"org.scala-lang:scala-library" -> "2.12.8",
            mod"test:b_2.12"                  -> "1.0.1"
          )

          assert(found == expected)

          val urls = res.dependencyArtifacts()
            .map(_._3.url.replace(handmadeMetadataBase, "file:///handmade-metadata/"))
            .toSet
          val expectedUrls = Set(
            "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.12.8/scala-library-2.12.8.jar",
            "file:///handmade-metadata/http/ivy.abc.com/test/b_2.12/1.0.1/jars/foo.jar",
            "file:///handmade-metadata/http/ivy.abc.com/test/b_2.12/1.0.1/zips/bzz.zip"
          )

          assert(urls == expectedUrls)

          val handmadeArtifacts = res.dependencyArtifacts()
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

          val urls = res.dependencyArtifacts()
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
                val versions         = c.dependencies.map(_.version)
                assert(versions == expectedVersions)
              case _ =>
                ???
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
                val versions         = c.dependencies.map(_.version)
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

        // default configuration "default(compile)" should fetch runtime JARs too ("default" scope pulls the runtime one)

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
            .withReconciliation(Seq(ModuleMatchers.all -> Reconciliation.Relaxed))

          val res = await {
            resolve
              .addDependencies(
                dep"org.webjars.npm:randomatic:1.1.7",
                dep"org.webjars.npm:is-odd:2.0.0"
              )
              .withResolutionParams(params)
              .future()
          }

          await(validateDependencies(res))

          val deps = res.minDependencies
          val isNumberVersions = deps.collect {
            case dep if dep.module == mod"org.webjars.npm:is-number" =>
              dep.version
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

        val subRes = res.subset(Seq(json4s))
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

        assert(res1.projectCache.contains((mod"io.get-coursier:coursier-cli_2.12", "1.1.0-M8")))
        assert(res1.projectCache.contains((mod"io.get-coursier:coursier-cli_2.12", "2.0.0-RC6-16")))
        assert {
          res1.finalDependenciesCache.keys.exists(dep =>
            dep.module == mod"io.get-coursier:coursier-cli_2.12" && dep.version == "1.1.0-M8"
          )
        }
        assert {
          res1.finalDependenciesCache.keys.exists(dep =>
            dep.module == mod"io.get-coursier:coursier-cli_2.12" && dep.version == "2.0.0-RC6-16"
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

        val depArtifacts = res.dependencyArtifacts(Some(Seq(Classifier.sources)))

        val urls = depArtifacts.map(_._3.url).toSet
        val expectedUrls = Set(
          "https://repo1.maven.org/maven2/org/tukaani/xz/1.2/xz-1.2-sources.jar",
          "https://repo1.maven.org/maven2/org/apache/commons/commons-compress/1.5/commons-compress-1.5-sources.jar"
        )
        assert(urls == expectedUrls)

        val pubTypes         = depArtifacts.map(_._2.`type`).toSet
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

        val depArtifacts = res.dependencyArtifacts()
        assert(depArtifacts.lengthCompare(1) == 0)

        val (_, pub, artifact) = depArtifacts.head

        val url = artifact.url
        val expectedUrl =
          "https://repo1.maven.org/maven2/io/grpc/protoc-gen-grpc-java/1.23.0/protoc-gen-grpc-java-1.23.0-linux-x86_64.exe"
        assert(artifact.url == expectedUrl)

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

    test("default value for pom project.packaging property") {
      async {
        val dep = dep"org.nd4j:nd4j-native-platform:1.0.0-beta4"
        val res = await {
          resolve
            .addDependencies(dep)
            .future()
        }

        await(validateDependencies(res))

        val urls = res.dependencyArtifacts().map(_._3.url)
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
        val urls = res.dependencyArtifacts().map(_._3.url).toSet

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

        val urls = res.dependencyArtifacts().map(_._3.url).toSet
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

        val artifacts = res.dependencyArtifacts()
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
          val params = resolve.resolutionParams.withJdkVersion("1.8.0_121")
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
          val params = resolve.resolutionParams.withJdkVersion("11.0.5")
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
            .addDependencies(dep"com.yahoo.athenz:athenz-zts-java-client-core:1.8.43")
            .addRepositories(mvn"https://yahoo.bintray.com/maven")
            .future()
        }
        await(validateDependencies(res))
      }
    }

    test("profile activation with missing property") {
      async {
        val res = await {
          resolve
            .addDependencies(dep"org.openjfx:javafx-base:18-ea+2")
            .future()
        }
        await(validateDependencies(res))

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
                dep.withVersion("2.12.12")
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
        profiles: Set[String] = Set.empty
      ): Future[Unit] =
        async {
          val params = ResolutionParams()
            .withJdkVersion("8.0")
            .withProfiles(profiles)
          val res = await {
            resolve
              .addDependencies(
                Dependency(
                  Module(
                    org"org.apache.spark",
                    ModuleName(s"spark-core_$scalaBinaryVersion"),
                    Map.empty
                  ),
                  sparkVersion
                )
              )
              .withResolutionParams(params)
              .future()
          }
          // await(validateDependencies(res))
          val found       = dependenciesWithRetainedVersion(res).map(_.moduleVersion).toMap
          val scalaLibOpt = found.get(mod"org.scala-lang:scala-library")
          assert(scalaLibOpt.exists(_.startsWith(s"$scalaBinaryVersion.")))
          // !
          await(validateDependencies(res, params))
        }

      test("scala 2_10") {
        test("spark 1_2_1") {
          check("1.2.1", "2.10")
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
          check("1.2.1", "2.11", profiles = Set("!scala-2.10", "scala-2.11"))
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
        check(dep"io.quarkus:quarkus-rest:3.15.1")
      }
      test("rest-jackson") {
        check(dep"io.quarkus:quarkus-rest-jackson:3.15.1")
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

      def androiCheck(dependencies: Dependency*): Future[Unit] =
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
        androiCheck(dep"androidx.activity:activity:1.8.2")
      }
      test("activity-compose") {
        androiCheck(dep"androidx.activity:activity-compose:1.8.2")
      }
      test("runtime") {
        androiCheck(dep"androidx.compose.runtime:runtime:1.3.1")
      }
      test("material3") {
        androiCheck(dep"androidx.compose.material3:material3:1.0.1")
      }
    }
  }
}
