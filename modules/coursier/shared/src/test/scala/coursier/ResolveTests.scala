package coursier

import coursier.core.Configuration
import coursier.error.ResolutionError
import coursier.ivy.IvyRepository
import coursier.params.{MavenMirror, Mirror, ResolutionParams, TreeMirror}
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

      val params = ResolutionParams()
        .withScalaVersion("2.12.7")

      val res = await {
        resolve
          .addDependencies(dep"sh.almond:scala-kernel_2.12.7:0.2.2")
          .addRepositories(Repositories.jitpack)
          .withResolutionParams(params)
          .future()
      }

      await(validateDependencies(res, params))
    }

    'typelevel - async {

      val params = ResolutionParams()
        .withScalaVersion("2.11.8")
        .withTypelevel(true)

      val res = await {
        resolve
          .addDependencies(dep"com.lihaoyi:ammonite_2.11.8:1.6.3")
          .withResolutionParams(params)
          .future()
      }

      await(validateDependencies(res, params))
    }

    'addForceVersion - async {

      val params = ResolutionParams()
        .withScalaVersion("2.12.8")
        .addForceVersion(mod"com.lihaoyi:upickle_2.12" -> "0.7.0")
        .addForceVersion(mod"io.get-coursier:coursier_2.12" -> "1.1.0-M6")

      val res = await {
        resolve
          .addDependencies(dep"com.lihaoyi:ammonite_2.12.8:1.6.3")
          .withResolutionParams(params)
          .future()
      }

      await(validateDependencies(res, params))

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
            .addDependencies(dep"com.netflix.karyon:karyon-eureka:1.0.28".copy(configuration = Configuration.defaultCompile))
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

        assert(res.isLeft)

        val error = res.left.get

        error match {
          case c: ResolutionError.ConflictingDependencies =>
            val expectedModules = Set(mod"io.netty:netty-codec-http2")
            val modules = c.dependencies.map(_.module)
            assert(modules == expectedModules)
            val expectedVersions = Set("[4.1.8.Final]", "[4.1.16.Final]")
            val versions = c.dependencies.map(_.version)
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
  }
}
