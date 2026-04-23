package coursier.parse

import coursier.cache.CachePolicy
import coursier.util.ValidationNel
import utest._

object CachePolicyParserTests extends TestSuite {

  private val defaults = Seq(
    CachePolicy.LocalOnly,
    CachePolicy.LocalOnlyIfValid,
    CachePolicy.LocalUpdateChanging
  )

  val tests = Tests {
    /** Verifies the `simple` scenario behaves as the user expects. */
    test("simple") {
      test {
        val res         = CachePolicyParser.cachePolicy("offline")
        val expectedRes = Right(CachePolicy.LocalOnly)
        assert(res == expectedRes)
      }
      test {
        val res         = CachePolicyParser.cachePolicy("update")
        val expectedRes = Right(CachePolicy.Update)
        assert(res == expectedRes)
      }
    }

    /** Verifies the `several` scenario behaves as the user expects. */
    test("several") {
      test {
        val res         = CachePolicyParser.cachePolicies("offline")
        val expectedRes = ValidationNel.success(Seq(CachePolicy.LocalOnly))
        assert(res == expectedRes)
      }
      test {
        val res         = CachePolicyParser.cachePolicies("update")
        val expectedRes = ValidationNel.success(Seq(CachePolicy.Update))
        assert(res == expectedRes)
      }

      /** Verifies the `default` scenario behaves as the user expects. */
      test("default") {
        val res         = CachePolicyParser.cachePolicies("default", defaults)
        val expectedRes = ValidationNel.success(defaults)
        assert(res == expectedRes)
      }

      /** Verifies the `noDefault` scenario behaves as the user expects. */
      test("noDefault") {
        val res = CachePolicyParser.cachePolicies("default")
        assert(!res.isSuccess)
      }

      test {
        val res         = CachePolicyParser.cachePolicies("offline,update", defaults)
        val expectedRes = ValidationNel.success(Seq(CachePolicy.LocalOnly, CachePolicy.Update))
        assert(res == expectedRes)
      }

      test {
        val res = CachePolicyParser.cachePolicies("offline,update,default", defaults)
        val expectedRes = ValidationNel.success(
          Seq(CachePolicy.LocalOnly, CachePolicy.Update) ++ defaults
        )
        assert(res == expectedRes)
      }

      test {
        val res = CachePolicyParser.cachePolicies("offline,update,default")
        assert(!res.isSuccess)
      }
    }
  }

}
