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
    'simple - {
      * - {
        val res = CachePolicyParser.cachePolicy("offline")
        val expectedRes = Right(CachePolicy.LocalOnly)
        assert(res == expectedRes)
      }
      * - {
        val res = CachePolicyParser.cachePolicy("update")
        val expectedRes = Right(CachePolicy.Update)
        assert(res == expectedRes)
      }
    }

    'several - {
      * - {
        val res = CachePolicyParser.cachePolicies("offline")
        val expectedRes = ValidationNel.success(Seq(CachePolicy.LocalOnly))
        assert(res == expectedRes)
      }
      * - {
        val res = CachePolicyParser.cachePolicies("update")
        val expectedRes = ValidationNel.success(Seq(CachePolicy.Update))
        assert(res == expectedRes)
      }

      'default - {
        val res = CachePolicyParser.cachePolicies("default", defaults)
        val expectedRes = ValidationNel.success(defaults)
        assert(res == expectedRes)
      }

      'noDefault - {
        val res = CachePolicyParser.cachePolicies("default")
        assert(!res.isSuccess)
      }

      * - {
        val res = CachePolicyParser.cachePolicies("offline,update", defaults)
        val expectedRes = ValidationNel.success(Seq(CachePolicy.LocalOnly, CachePolicy.Update))
        assert(res == expectedRes)
      }

      * - {
        val res = CachePolicyParser.cachePolicies("offline,update,default", defaults)
        val expectedRes = ValidationNel.success(Seq(CachePolicy.LocalOnly, CachePolicy.Update) ++ defaults)
        assert(res == expectedRes)
      }

      * - {
        val res = CachePolicyParser.cachePolicies("offline,update,default")
        assert(!res.isSuccess)
      }
    }
  }

}
