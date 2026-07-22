package coursier.cache

import java.net.ServerSocket
import java.util.concurrent.{ExecutorService, Executors}

import scala.concurrent.ExecutionContext

import io.undertow.Undertow
import utest._

import coursier.{Fetch, Resolve}
import coursier.cache.TestUtil._
import coursier.cache.server.CacheServer
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

  private def withCacheServer[T](cache: FileCache[Task])(f: String => T): T =
    withExecutorService(Executors.newCachedThreadPool()) { requestsPool =>
      val port   = freePort()
      val server = Undertow.builder()
        .addHttpListener(port, "localhost")
        .setHandler(CacheServer.handler(cache, ExecutionContext.fromExecutorService(requestsPool)))
        .build()
      server.start()
      try f(s"http://localhost:$port")
      finally server.stop()
    }

  private def withRemoteCache[T](f: (os.Path, RemoteCache[Task]) => T): T =
    withTmpDir { dir =>
      withExecutorService(Executors.newFixedThreadPool(4)) { serverPool =>
        val cacheDir    = dir / "cache"
        val serverCache = FileCache[Task](cacheDir.toIO)
          .withPool(serverPool)

        withCacheServer(serverCache) { cacheServerUrl =>
          withExecutorService(Executors.newCachedThreadPool()) { remotePool =>
            val remoteCache = RemoteCache[Task](cacheServerUrl, cacheDir.toIO)
              .withPool(remotePool)
              .withWatchLenPool(remotePool)
            f(cacheDir, remoteCache)
          }
        }
      }
    }

  val tests = Tests {

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
          .withRepositories(Seq(MavenRepository(central)))
          .withCache(remoteCache)
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
