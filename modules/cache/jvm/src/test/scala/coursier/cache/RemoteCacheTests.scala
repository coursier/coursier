package coursier.cache

import java.net.{ServerSocket, URI}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.util.concurrent.{ExecutorService, Executors}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

import io.undertow.Undertow
import utest._

import coursier.{Fetch, Resolve}
import coursier.cache.TestUtil._
import coursier.cache.server.{CacheServer, UrlFilter}
import coursier.maven.MavenRepository
import coursier.util.{Artifact, Task}
import coursier.util.StringInterpolators._

object RemoteCacheTests extends TestSuite {

  private val central            = "https://repo1.maven.org/maven2"
  private val scalaLibraryPomUrl =
    s"$central/org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.pom"

  private def freePort(): Int = {
    val socket = new ServerSocket(0)
    try socket.getLocalPort
    finally socket.close()
  }

  private def withExecutorService[T](pool: ExecutorService)(f: ExecutorService => T): T =
    try f(pool)
    finally pool.shutdownNow()

  private def withCacheServer[T](
    cache: FileCache[Task],
    urlFilter: UrlFilter
  )(f: String => T): T =
    withExecutorService(Executors.newCachedThreadPool()) { requestsPool =>
      val port    = freePort()
      val handler =
        CacheServer.handler(cache, ExecutionContext.fromExecutorService(requestsPool), urlFilter)
      val server = Undertow.builder()
        .addHttpListener(port, "localhost")
        .setHandler(handler)
        .build()
      server.start()
      try f(s"http://localhost:$port")
      finally server.stop()
    }

  private def withRemoteCache[T](f: (os.Path, RemoteCache[Task]) => T): T =
    withRemoteCache0(identity)((cacheDir, _, remoteCache) => f(cacheDir, remoteCache))

  private def withRemoteCache0[T](
    customizeServerCache: FileCache[Task] => FileCache[Task],
    urlFilter: UrlFilter = UrlFilter.allowAll
  )(f: (os.Path, String, RemoteCache[Task]) => T): T =
    withTmpDir { dir =>
      withExecutorService(Executors.newFixedThreadPool(4)) { serverPool =>
        val cacheDir    = dir / "cache"
        val serverCache = customizeServerCache(FileCache[Task](cacheDir.toIO))
          .copy(pool = serverPool)

        withCacheServer(serverCache, urlFilter) { cacheServerUrl =>
          withExecutorService(Executors.newCachedThreadPool()) { remotePool =>
            val remoteCache = RemoteCache[Task](cacheServerUrl, cacheDir.toIO).copy(
              pool = remotePool,
              watchLenPool = remotePool
            )
            f(cacheDir, cacheServerUrl, remoteCache)
          }
        }
      }
    }

  /** An upstream server serving `/changing.txt` and `/other.txt`, and 404 for anything else */
  private def withUpstream[T](f: (String, RequestLog) => T): T = {
    val log    = new RequestLog
    val server = new RawHttpServer(
      log,
      entry =>
        if (entry.path == "/changing.txt" || entry.path == "/other.txt")
          RawHttpServer.ok("hello".getBytes(StandardCharsets.UTF_8))
        else
          RawHttpServer.Response("HTTP/1.1 404 Not Found", Seq("Content-Length" -> "0"))
    )
    try f(server.baseUrl, log)
    finally server.close()
  }

  private def upstreamRequests(log: RequestLog, path: String): Int =
    log.entries.count(_.path == path)

  private def get(remoteCache: RemoteCache[Task], artifact: Artifact) =
    remoteCache.file(artifact).run.unsafeRun(wrapExceptions = true)(remoteCache.ec)

  private def postRequest(cacheServerUrl: String, path: String, body: String) = {
    val client  = HttpClient.newHttpClient()
    val request = HttpRequest.newBuilder(new URI(s"$cacheServerUrl$path"))
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build()
    client.send(request, HttpResponse.BodyHandlers.ofString())
  }

  private def postGetRequest(cacheServerUrl: String, body: String): String =
    postRequest(cacheServerUrl, "/get", body).body()

  val tests = Tests {

    test("TTL is sent to the server") {
      withUpstream { (upstreamUrl, log) =>
        // the server never re-checks changing artifacts on its own
        withRemoteCache0(_.copy(ttl = Some(Duration.Inf))) { (_, _, remoteCache) =>
          val artifact = Artifact(s"$upstreamUrl/changing.txt").copy(changing = true)

          val first = get(remoteCache.copy(ttl = Some(Duration.Inf)), artifact)
          assert(first.isRight)
          assert(upstreamRequests(log, "/changing.txt") > 0)

          log.reset()
          val withInfiniteTtl = get(remoteCache.copy(ttl = Some(Duration.Inf)), artifact)
          assert(withInfiniteTtl.isRight)
          assert(upstreamRequests(log, "/changing.txt") == 0)

          log.reset()
          val withZeroTtl = get(remoteCache.copy(ttl = Some(Duration.Zero)), artifact)
          assert(withZeroTtl.isRight)
          assert(upstreamRequests(log, "/changing.txt") > 0)
        }
      }
    }

    test("cache policies are sent to the server") {
      withUpstream { (upstreamUrl, log) =>
        withRemoteCache0(identity) { (_, _, remoteCache) =>
          val artifact = Artifact(s"$upstreamUrl/other.txt")

          val offline = get(remoteCache.copy(cachePolicies = Seq(CachePolicy.LocalOnly)), artifact)
          // errors come back from the server as download errors, with the original type in the message
          assert(offline.left.exists(_.describe.contains("not found")))
          assert(upstreamRequests(log, "/other.txt") == 0)

          val online = get(remoteCache, artifact)
          assert(online.isRight)
          assert(upstreamRequests(log, "/other.txt") > 0)
        }
      }
    }

    test("server rejects unknown cache policies and malformed TTLs") {
      withRemoteCache0(identity) { (_, cacheServerUrl, _) =>
        val unknownPolicy = postGetRequest(
          cacheServerUrl,
          """{"artifact":{"url":"https://example.com/a"},"cachePolicies":["Nope"]}"""
        )
        assert(unknownPolicy.contains("Unknown cache policy 'Nope'"))

        val malformedTtl = postGetRequest(
          cacheServerUrl,
          """{"artifact":{"url":"https://example.com/a"},"ttl":"soon"}"""
        )
        assert(malformedTtl.contains("Malformed TTL 'soon'"))
      }
    }

    test("server rejects disallowed URLs") {
      withUpstream { (upstreamUrl, log) =>
        val urlFilter = new UrlFilter(defaultAllow = true, Nil, Seq(upstreamUrl))
        withRemoteCache0(identity, urlFilter) { (cacheDir, cacheServerUrl, remoteCache) =>
          val url = s"$upstreamUrl/other.txt"

          for (path <- Seq("/get", "/path")) {
            val response = postRequest(cacheServerUrl, path, s"""{"artifact":{"url":"$url"}}""")
            assert(response.statusCode() == 403)
            assert(response.body() == s"Forbidden URL: $url")
          }

          val res = get(remoteCache, Artifact(url))
          assert(res.left.exists(_.describe.contains("HTTP 403")))
          assert(upstreamRequests(log, "/other.txt") == 0)

          // URLs not matching the filter are still fetched
          val allowed = get(remoteCache, Artifact(scalaLibraryPomUrl))
          assert(allowed.isRight)
          assert(os.walk(cacheDir).forall(!_.last.startsWith("other.txt")))
        }
      }
    }

    test("server only accepts allowed URLs") {
      withUpstream { (upstreamUrl, log) =>
        val allowedUrl = s"$upstreamUrl/other.txt"
        val urlFilter  = new UrlFilter(defaultAllow = false, Seq(allowedUrl), Nil)
        withRemoteCache0(identity, urlFilter) { (_, cacheServerUrl, remoteCache) =>
          val allowed = get(remoteCache, Artifact(allowedUrl))
          assert(allowed.isRight)
          assert(upstreamRequests(log, "/other.txt") > 0)

          val disallowed = get(remoteCache, Artifact(s"$upstreamUrl/changing.txt"))
          assert(disallowed.left.exists(_.describe.contains("HTTP 403")))
          assert(upstreamRequests(log, "/changing.txt") == 0)

          val pom = get(remoteCache, Artifact(scalaLibraryPomUrl))
          assert(pom.left.exists(_.describe.contains("HTTP 403")))

          // malformed requests are still reported as such
          val malformed = postGetRequest(cacheServerUrl, "{")
          assert(malformed == "Internal error")
        }
      }
    }

    test("server checks checksum and extra artifact URLs too") {
      withUpstream { (upstreamUrl, log) =>
        val allowedUrl    = s"$upstreamUrl/other.txt"
        val disallowedUrl = s"$upstreamUrl/changing.txt"
        val urlFilter     = new UrlFilter(defaultAllow = false, Seq(allowedUrl), Nil)
        withRemoteCache0(identity, urlFilter) { (_, cacheServerUrl, remoteCache) =>
          val withChecksum = Artifact(allowedUrl).copy(checksumUrls = Map("SHA-1" -> disallowedUrl))
          val checksumRes  = get(remoteCache, withChecksum)
          assert(checksumRes.left.exists(_.describe.contains(s"Forbidden URL: $disallowedUrl")))

          val nestedBody =
            s"""{"artifact":{"url":"$allowedUrl","extra":{"sig":{"url":"$disallowedUrl"}}}}"""
          val nested = postRequest(cacheServerUrl, "/get", nestedBody)
          assert(nested.statusCode() == 403)
          assert(nested.body() == s"Forbidden URL: $disallowedUrl")

          assert(upstreamRequests(log, "/other.txt") == 0)
          assert(upstreamRequests(log, "/changing.txt") == 0)
        }
      }
    }

    test("get POM") {
      withRemoteCache { (_, remoteCache) =>
        val artifact = Artifact(scalaLibraryPomUrl)

        val file = remoteCache.file(artifact).run
          .unsafeRun(wrapExceptions = true)(remoteCache.ec)
          .fold(e => throw e, os.Path(_))

        val content = os.read(file)
        assert(content.contains("<artifactId>scala-library</artifactId>"))
        assert(content.contains("<version>2.13.16</version>"))
      }
    }

    test("resolve dependency") {
      withRemoteCache { (_, remoteCache) =>
        val dependency = dep"org.scala-lang:scala-compiler:2.13.16"
        val resolution = Resolve()
          .noMirrors
          .copy(repositories = Seq(MavenRepository(central)))
          .copy(cache = remoteCache)
          .addDependencies(dependency)
          .run()(remoteCache.ec)

        val deps = resolution.orderedDependencies.map { d =>
          d.module.repr + ":" + d.versionConstraint.asString
        }
        val expectedDeps = Seq(
          "org.scala-lang:scala-compiler:2.13.16",
          "org.scala-lang:scala-library:2.13.16",
          "org.scala-lang:scala-reflect:2.13.16",
          "io.github.java-diff-utils:java-diff-utils:4.15",
          "org.jline:jline:3.27.1"
        )

        assert(deps == expectedDeps)
      }
    }

    test("fetch dependency artifacts") {
      withRemoteCache { (cacheDir, remoteCache) =>
        val dependency = dep"org.scala-lang:scala-compiler:2.13.16"
        val result     = Fetch()
          .noMirrors
          .withRepositories(Seq(MavenRepository(central)))
          .withCache(remoteCache)
          .addDependencies(dependency)
          .runResult()(remoteCache.ec)

        val files = result.files.map(os.Path(_))

        assert(files.forall(_.startsWith(cacheDir)))
        val files0 = files.map(_.subRelativeTo(cacheDir))

        val expectedFiles = Seq(
          os.sub / "https/repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.13.16/scala-compiler-2.13.16.jar",
          os.sub / "https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.jar",
          os.sub / "https/repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.16/scala-reflect-2.13.16.jar",
          os.sub / "https/repo1.maven.org/maven2/io/github/java-diff-utils/java-diff-utils/4.15/java-diff-utils-4.15.jar",
          os.sub / "https/repo1.maven.org/maven2/org/jline/jline/3.27.1/jline-3.27.1-jdk8.jar"
        )

        assert(files0 == expectedFiles)
      }
    }
  }
}
