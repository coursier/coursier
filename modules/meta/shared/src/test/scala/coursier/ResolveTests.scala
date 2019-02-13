package coursier

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
  }
}
