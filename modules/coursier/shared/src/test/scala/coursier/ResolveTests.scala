package coursier

import coursier.params.{MavenMirror, Mirror, ResolutionParams, TreeMirror}
import utest._

import scala.async.Async.{async, await}

object ResolveTests extends TestSuite {

  import TestHelpers.{ec, cache, validateDependencies, versionOf}

  val tests = Tests {
    'simple - async {

      val res = await {
        Resolve()
          .noMirrors
          .withCache(cache)
          .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
          .future()
      }

      await(validateDependencies(res))
    }

    'forceScalaVersion - async {

      val params = ResolutionParams()
        .withScalaVersion("2.12.7")

      val res = await {
        Resolve()
          .noMirrors
          .withCache(cache)
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
        Resolve()
          .noMirrors
          .withCache(cache)
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
        Resolve()
          .noMirrors
          .withCache(cache)
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
          Resolve()
            .noMirrors
            .withCache(cache)
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

      val resolve0 = Resolve()
        .noMirrors
        .withCache(cache)
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
    }
  }
}
