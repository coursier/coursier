package coursier.jvm

import coursier.{Repositories, Resolve}
import coursier.cache.ArchiveCache
import coursier.parse.ModuleParser
import coursier.testcache.TestCache
import coursier.util.StringInterpolators._
import coursier.util.Task
import utest._

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext

object CentralIndexTests extends TestSuite {

  val cache = {
    val cache0 = TestCache.cache[Task]
    cache0
      .withDummyArtifact { a =>
        cache0.dummyArtifact(a) &&
        // don't clear indices JARs in snapshots
        !(a.url.contains("/coursier/jvm/indices/") && a.url.endsWith(".jar"))
      }
  }
  private implicit def ec: ExecutionContext = cache.ec

  private def channel(os: String, arch: String) = JvmChannel.module(
    JvmChannel.centralModule(os, arch)
  )

  val tests = Tests {
    test("alpine-x64-zulu17") {
      val os   = "linux-musl"
      val arch = "amd64"
      async {
        val index = await {
          JvmIndex.load(
            cache = cache,
            repositories = Resolve().repositories,
            indexChannel = channel(os, arch),
            os = Some(os),
            arch = Some(arch)
          ).future()
        }

        val zulu17 =
          index.lookup("zulu", "17+", os = Some(os), arch = Some(arch)).map(_.lastOption.map(_.url))
        val expectedZulu17 = Right(
          Some("https://cdn.azul.com/zulu/bin/zulu17.54.21-ca-jdk17.0.13-linux_musl_x64.tar.gz")
        )
        assert(zulu17 == expectedZulu17)
      }
    }

    test("macos-arm-temurin21") {
      val os   = "darwin"
      val arch = "arm64"
      async {
        val index = await {
          JvmIndex.load(
            cache = cache,
            repositories = Resolve().repositories,
            indexChannel = channel(os, arch),
            os = Some(os),
            arch = Some(arch)
          ).future()
        }

        val temurin21 = index.lookup("temurin", "21+", os = Some(os), arch = Some(arch))
          .map(_.lastOption.map(_.url))
        val expectedTemurin21 = Right(Some(
          "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.5%2B11/OpenJDK21U-jdk_aarch64_mac_hotspot_21.0.5_11.tar.gz"
        ))
        assert(temurin21 == expectedTemurin21)
      }
    }
  }
}
