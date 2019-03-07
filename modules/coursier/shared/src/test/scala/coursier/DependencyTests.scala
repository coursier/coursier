package coursier

import utest._

import scala.async.Async.{async, await}

object DependencyTests extends TestSuite {

  import TestHelpers.{ec, cache, validateDependencies}

  val tests = Tests {

    'hadoopClient - async {
      val res = await {
        Resolve()
          .addDependencies(dep"org.apache.hadoop:hadoop-client:3.2.0")
          .withCache(cache)
          .future()
      }

      await(validateDependencies(res))
    }

  }

}
