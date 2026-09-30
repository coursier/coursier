package coursier.cache.server

import com.github.plokhotnyuk.jsoniter_scala.core._
import utest._

import scala.concurrent.duration.{Duration, DurationInt}

import coursier.cache.CachePolicy

object ModelTests extends TestSuite {

  import Model._

  val tests = Tests {

    test("TTL round-trip") {
      val ttls =
        Seq(None, Some(Duration.Zero), Some(24.hours), Some(1500.millis), Some(Duration.Inf))
      for (ttl <- ttls) {
        val serialized = serializeTtl(ttl)
        val parsed     = parseTtl(serialized)
        assert(parsed == Right(ttl))
      }
    }

    test("malformed TTL") {
      assert(parseTtl("soon").isLeft)
    }

    test("cache policy round-trip") {
      val policies = Seq(
        CachePolicy.LocalOnly,
        CachePolicy.LocalOnlyIfValid,
        CachePolicy.LocalUpdateChanging,
        CachePolicy.LocalUpdate,
        CachePolicy.UpdateChanging,
        CachePolicy.Update,
        CachePolicy.FetchMissing,
        CachePolicy.ForceDownload,
        CachePolicy.NoChanging.LocalOnly,
        CachePolicy.NoChanging.LocalUpdate,
        CachePolicy.NoChanging.FetchMissing,
        CachePolicy.NoChanging.ForceDownload
      )
      for (policy <- policies)
        assert(parseCachePolicy(cachePolicyName(policy)) == Some(policy))
    }

    test("requests from former clients") {
      val request = readFromString[GetRequest]("""{"artifact":{"url":"https://example.com/a"}}""")
      assert(request.cachePolicies.isEmpty)
      assert(request.ttl.isEmpty)
    }

    test("unknown fields are ignored") {
      // what servers predating a field do with it, as they use the same codec configuration
      val request = readFromString[GetRequest](
        """{"artifact":{"url":"https://example.com/a"},"somethingNew":["a"]}"""
      )
      assert(request.artifact.url == "https://example.com/a")
    }
  }
}
