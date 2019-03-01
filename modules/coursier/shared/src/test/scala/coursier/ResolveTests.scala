package coursier

import coursier.params.ResolutionParams
import coursier.util.Repositories
import utest._

import scala.async.Async.{async, await}

object ResolveTests extends TestSuite {

  import TestHelpers.{ec, cache, validateDependencies, versionOf}

  val tests = Tests {
    'simple - async {

      val res = await {
        Resolve()
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
  }
}
