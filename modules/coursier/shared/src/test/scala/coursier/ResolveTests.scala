package coursier

import coursier.core.{Configuration, Reconciliation}
import coursier.error.ResolutionError
import coursier.ivy.IvyRepository
import coursier.params.{MavenMirror, Mirror, ResolutionParams, TreeMirror}
import coursier.util.ModuleMatchers
import utest._

import scala.async.Async.{async, await}

object ResolveTests extends TestSuite {

  import TestHelpers.{ec, cache, dependenciesWithRetainedVersion, handmadeMetadataBase, validateDependencies, versionOf}

  private val resolve = Resolve()
    .noMirrors
    .withCache(cache)

  val tests = Tests {

    'simple - async {

      val res = await {
        resolve
          .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
          .future()
      }

      await(validateDependencies(res))
    }

    'forceScalaVersion - async {

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

    'typelevel - async {

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

    'addForceVersion - async {

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

      val upickleVersionOpt = versionOf(res, mod"com.lihaoyi:upickle_2.12")
      val expectedUpickleVersion = "0.7.0"
      assert(upickleVersionOpt.contains(expectedUpickleVersion))

      val coursierVersionOpt = versionOf(res, mod"io.get-coursier:coursier_2.12")
      val expectedCoursierVersion = "1.1.0-M6"
      assert(coursierVersionOpt.contains(expectedCoursierVersion))
    }

    'mirrors - {

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

      'mavenMirror - {
        'specific - run(MavenMirror("https://jcenter.bintray.com", "https://repo1.maven.org/maven2"))
        'all - run(MavenMirror("https://jcenter.bintray.com", "*"))

        'trailingSlash - {
          'specific - {
            * - run(MavenMirror("https://jcenter.bintray.com/", "https://repo1.maven.org/maven2"))
            * - run(MavenMirror("https://jcenter.bintray.com", "https://repo1.maven.org/maven2/"))
            * - run(MavenMirror("https://jcenter.bintray.com/", "https://repo1.maven.org/maven2/"))
          }
          'all - run(MavenMirror("https://jcenter.bintray.com/", "*"))
        }
      }

      'treeMirror - {
        * - run(TreeMirror("https://jcenter.bintray.com", "https://repo1.maven.org/maven2"))
        'trailingSlash - {
          * - run(TreeMirror("https://jcenter.bintray.com/", "https://repo1.maven.org/maven2"))
          * - run(TreeMirror("https://jcenter.bintray.com", "https://repo1.maven.org/maven2/"))
          * - run(TreeMirror("https://jcenter.bintray.com/", "https://repo1.maven.org/maven2/"))
        }
      }
    }

    'latest - {
      'maven - {

        val resolve0 = resolve
          .withRepositories(Seq(
            Repositories.sonatype("snapshots"),
            Repositories.central
          ))

        'integration - async {

          val res = await {
            resolve0
              .addDependencies(dep"com.chuusai:shapeless_2.12:latest.integration")
              .future()
          }

          await(validateDependencies(res))
        }

        'release - async {

          val res = await {
            resolve0
              .addDependencies(dep"com.chuusai:shapeless_2.12:latest.release")
              .future()
          }

          await(validateDependencies(res))
        }

        'stable - async {

          val res = await {
            resolve0
              .addDependencies(dep"com.lihaoyi:ammonite_2.12.8:latest.stable")
              .future()
          }

          val found = dependenciesWithRetainedVersion(res).map(_.moduleVersion).toMap
          val ammVersionOpt = found.get(mod"com.lihaoyi:ammonite_2.12.8")
          assert(ammVersionOpt.exists(_.split('.').length == 3))
          assert(ammVersionOpt.exists(!_.contains("-")))

          await(validateDependencies(res))
        }

        'withInterval - {
          'in - async {

            val res = await {
              resolve0
                .addDependencies(
                  dep"com.chuusai:shapeless_2.12:latest.release",
                  dep"com.chuusai:shapeless_2.12:2.3+"
                )
                .future()
            }

            await(validateDependencies(res))
          }

          'out - async {

            val res = await {
              resolve0
                .addDependencies(
                  dep"com.chuusai:shapeless_2.12:latest.release",
                  dep"com.chuusai:shapeless_2.12:[2.3.0,2.3.3)"
                )
                .io
                .attempt
                .future()
            }


            val isLeft = res.isLeft
            assert(isLeft)

            val error = res.left.get

            error match {
              case e: ResolutionError.CantDownloadModule =>
                assert(e.module == mod"com.chuusai:shapeless_2.12")
              case _ =>
                throw error
            }
          }
        }
      }

      'ivy - {

        val resolve0 = resolve
          .withRepositories(Seq(
            Repositories.central,
            IvyRepository.parse(handmadeMetadataBase + "http/ivy.abc.com/[defaultPattern]")
              .fold(sys.error, identity)
          ))

        'integration - async {

          val res = await {
            resolve0
              .addDependencies(dep"test:a_2.12:latest.integration")
              .future()
          }

          val found = dependenciesWithRetainedVersion(res).map(_.moduleVersion).toSet
          val expected = Set(
            mod"org.scala-lang:scala-library" -> "2.12.8",
            mod"test:a_2.12" -> "1.0.2-SNAPSHOT"
          )

          assert(found == expected)
        }

        'release - async {

          val res = await {
            resolve0
              .addDependencies(dep"test:a_2.12:latest.release")
              .future()
          }

          val found = dependenciesWithRetainedVersion(res).map(_.moduleVersion).toSet
          val expected = Set(
            mod"org.scala-lang:scala-library" -> "2.12.8",
            mod"test:a_2.12" -> "1.0.1"
          )

          assert(found == expected)
        }
      }
    }

    'ivyLatestSubRevision - {
      'zero - {
        * - async {

          val res = await {
            resolve
              .addDependencies(dep"io.circe:circe-core_2.12:0+")
              .future()
          }

          await(validateDependencies(res))
        }

        * - async {

          val res = await {
            resolve
              .addDependencies(dep"io.circe:circe-core_2.12:0.11+")
              .future()
          }

          await(validateDependencies(res))
        }
      }

      'nonZero - async {

        val res = await {
          resolve
            .addDependencies(dep"com.chuusai:shapeless_2.12:2.3+")
            .future()
        }

        await(validateDependencies(res))
      }

      'plusInVersion - async {

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
          mod"test:b_2.12" -> "1.0.2+20190524-1"
        )

        assert(found == expected)
      }
    }

    'exclusions - {

      val resolve0 = resolve
        .addDependencies(dep"com.github.alexarchambault:argonaut-shapeless_6.2_2.12:1.2.0-M11")

      'check - async {
        val res = await {
          resolve0
            .future()
        }

        val argonaut = res.minDependencies.filter(_.module.organization == org"io.argonaut")
        assert(argonaut.nonEmpty)

        await(validateDependencies(res))
      }

      'test - async {
        val res = await {
          resolve0
            .mapResolutionParams(_.addExclusions((org"io.argonaut", name"*")))
            .future()
        }

        val argonaut = res.minDependencies.filter(_.module.organization == org"io.argonaut")
        assert(argonaut.isEmpty)

        await(validateDependencies(res))
      }

      "no org" - async {

        val res = await {
          resolve
            .addDependencies(dep"com.netflix.karyon:karyon-eureka:1.0.28".withConfiguration(Configuration.defaultCompile))
            .future()
        }

        await(validateDependencies(res))
      }
    }

    'conflicts - {
      * - async {

        // hopefully, that's a legit conflict (not one that ought to go away after possible fixes in Resolution)

        val res = await {
          resolve
            .addDependencies(dep"org.apache.beam:beam-sdks-java-io-google-cloud-platform:2.3.0")
            .io
            .attempt
            .future()
        }

        val isLeft = res.isLeft
        assert(isLeft)

        val error = res.left.get

        error match {
          case c: ResolutionError.ConflictingDependencies =>
            val expectedModules = Set(mod"io.netty:netty-codec-http2", mod"io.grpc:grpc-core")
            val modules = c.dependencies.map(_.module)
            assert(modules == expectedModules)
            val expectedVersions = Map(
              mod"io.netty:netty-codec-http2" -> Set("[4.1.8.Final]", "[4.1.16.Final]"),
              mod"io.grpc:grpc-core" -> Set("1.2.0", "1.5.0", "1.6.1", "1.7.0", "[1.2.0]", "[1.7.0]")
            )
            val versions = c.dependencies.groupBy(_.module).mapValues(_.map(_.version)).iterator.toMap
            assert(versions == expectedVersions)
          case _ =>
            ???
        }
      }
    }

    "parent / import scope" - {
      * - async {

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

    "properties" - {
      "in packaging" - async {
        val res = await {
          resolve
            .addDependencies(dep"javax.ws.rs:javax.ws.rs-api:2.1.1")
            .future()
        }

        await(validateDependencies(res))

        val urls = res.dependencyArtifacts().map(_._3.url).toSet
        val expectedUrls = Set("https://repo1.maven.org/maven2/javax/ws/rs/javax.ws.rs-api/2.1.1/javax.ws.rs-api-2.1.1.jar")

        assert(urls == expectedUrls)
      }
    }

    "ivy" - {
      "publication name" - async {

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
          mod"test:b_2.12" -> "1.0.1"
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

    "version intervals" - {
      "0 lower bound" - async {

        val res = await {
          resolve
            .addDependencies(dep"org.webjars.npm:dom-serializer:[0,1)")
            .future()
        }

        await(validateDependencies(res))
      }

      "conflict with specific version" - {
        * - async {

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

          val error = res.left.get

          error match {
            case c: ResolutionError.ConflictingDependencies =>
              val expectedModules = Set(mod"org.scala-lang:scala-library")
              val modules = c.dependencies.map(_.module)
              assert(modules == expectedModules)
              val expectedVersions = Set("2.12+", "2.13.0")
              val versions = c.dependencies.map(_.version)
              assert(versions == expectedVersions)
            case _ =>
              ???
          }
        }

        * - async {

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

          val error = res.left.get

          error match {
            case c: ResolutionError.ConflictingDependencies =>
              val expectedModules = Set(mod"com.chuusai:shapeless_2.12")
              val modules = c.dependencies.map(_.module)
              assert(modules == expectedModules)
              val expectedVersions = Set("[2.3.0,2.3.3)", "2.3.3")
              val versions = c.dependencies.map(_.version)
              assert(versions == expectedVersions)
            case _ =>
              throw error
          }
        }
      }
    }

    "override dependency from profile" - {

      * - async {

        val resolve0 = resolve
          .mapResolutionParams(_
            .withUseSystemOsInfo(false)
            .withUseSystemJdkVersion(false)
          )
          .addDependencies(dep"io.netty:netty-transport-native-epoll:4.1.34.Final")

        val res = await {
          resolve0
            .future()
        }

        val unixCommonDepOpt = res.minDependencies.find(_.module == mod"io.netty:netty-transport-native-unix-common")
        assert(unixCommonDepOpt.exists(!_.optional))

        await(validateDependencies(res, resolve0.resolutionParams))
      }

      * - async {

        val resolve0 = resolve
          .mapResolutionParams(_
            .withUseSystemOsInfo(false)
            .withUseSystemJdkVersion(false)
            .withOsInfo(coursier.core.Activation.Os.fromProperties(Map(
              "os.name" -> "Linux",
              "os.arch" -> "amd64",
              "os.version" -> "4.9.125",
              "path.separator" -> ":"
            )))
          )
          .addDependencies(dep"io.netty:netty-transport-native-epoll:4.1.34.Final")

        val res = await {
          resolve0
            .future()
        }

        val unixCommonDepOpt = res.minDependencies.find(_.module == mod"io.netty:netty-transport-native-unix-common")
        assert(unixCommonDepOpt.exists(!_.optional))

        await(validateDependencies(res, resolve0.resolutionParams))
      }

      * - async {

        val resolve0 = resolve
          .mapResolutionParams(_
            .withUseSystemOsInfo(false)
            .withUseSystemJdkVersion(false)
            .withOsInfo(coursier.core.Activation.Os.fromProperties(Map(
              "os.name" -> "Mac OS X",
              "os.arch" -> "x86_64",
              "os.version" -> "10.14.5",
              "path.separator" -> ":"
            )))
          )
          .addDependencies(dep"io.netty:netty-transport-native-epoll:4.1.34.Final")

        val res = await {
          resolve0
            .future()
        }

        val unixCommonDepOpt = res.minDependencies.find(_.module == mod"io.netty:netty-transport-native-unix-common")
        assert(unixCommonDepOpt.exists(!_.optional))

        await(validateDependencies(res, resolve0.resolutionParams))
      }
    }

    "runtime dependencies" - async {

      // default configuration "default(compile)" should fetch runtime JARs too ("default" scope pulls the runtime one)

      val res: coursier.core.Resolution = await {
        resolve
          .addDependencies(dep"com.almworks.sqlite4java:libsqlite4java-linux-amd64:1.0.392")
          .future()
      }

      await(validateDependencies(res))

      val artifacts = res.artifacts(types = Resolution.defaultTypes + Type("so"))
      val urls = artifacts.map(_.url).toSet
      val expectedUrls = Set(
        "https://repo1.maven.org/maven2/com/almworks/sqlite4java/sqlite4java/1.0.392/sqlite4java-1.0.392.jar",
        "https://repo1.maven.org/maven2/com/almworks/sqlite4java/libsqlite4java-linux-amd64/1.0.392/libsqlite4java-linux-amd64-1.0.392.so",
        // this one doesn't exist, but should be marked as optional anyway
        "https://repo1.maven.org/maven2/com/almworks/sqlite4java/libsqlite4java-linux-amd64/1.0.392/libsqlite4java-linux-amd64-1.0.392.jar"
      )

      assert(urls == expectedUrls)
    }

    "relaxed reconciliation" - {
      * - async {
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

    "subset" - async {
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

    "config handling" - async {

      // if config handling gets messed up, like the "default" config of some dependencies ends up being pulled
      // where it shouldn't, this surfaces more easily here, as sbt-ci-release depends on other sbt plugins,
      // only available on Ivy repositories and not having a configuration named "default".
      val res = await {
        resolve
          .addDependencies(dep"com.geirsson:sbt-ci-release;scalaVersion=2.12;sbtVersion=1.0:1.2.6")
          .addRepositories(Repositories.sbtPlugin("releases"))
          .future()
      }

      await(validateDependencies(res))
    }
  }
}
