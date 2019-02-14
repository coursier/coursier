package coursier

import coursier.params.ResolutionParams
import coursier.util.Repositories
import utest._

import scala.async.Async.{async, await}

object ResolveTests extends TestSuite {

  import TestHelpers.{ec, cache, validateDependencies}


  val tests = Tests {
    'simple - async {

      val res = await {
        Resolve.resolveFuture(
          Seq(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8"),
          cache = cache
        )
      }

      await(validateDependencies(res))
    }

    'forceScalaVersion - async {

      val params = ResolutionParams()
        .withScalaVersion("2.12.7")
        .withForceScalaVersion(true)

      val res = await {
        Resolve.resolveFuture(
          Seq(dep"sh.almond:scala-kernel_2.12.7:0.2.2"),
          repositories = Resolve.defaultRepositories ++ Seq(
            Repositories.jitpack
          ),
          params = params,
          cache = cache
        )
      }

      await(validateDependencies(res, params))
    }
  }
}
