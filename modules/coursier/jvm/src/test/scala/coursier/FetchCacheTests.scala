package coursier

import java.nio.file.{Files, Path}

import coursier.cache.FileCache
import utest._

import scala.async.Async.{async, await}
import scala.collection.JavaConverters._

object FetchCacheTests extends TestSuite {

  private def remove(d: Path)(f: String => Boolean): Int =
    if (Files.isDirectory(d))
      Files.list(d)
        .iterator()
        .asScala
        .map(remove(_)(f))
        .sum
    else if (f(d.getFileName.toString) && Files.deleteIfExists(d))
      1
    else
      0

  private def delete(d: Path): Unit =
    if (Files.isDirectory(d))
      Files.list(d)
        .iterator()
        .asScala
        .foreach(delete)
    else
      Files.deleteIfExists(d)

  val tests = Tests {

    import TestHelpers.ec

    'simple - async {

      val tmpCache = Files.createTempDirectory("coursier-cache-tests")
      val tmpFetchCache = Files.createTempDirectory("coursier-fetch-cache-tests")

      val shutdownHook: Thread =
        new Thread("cleanup") {
          override def run() = {
            delete(tmpCache)
            delete(tmpFetchCache)
          }
        }
      Runtime.getRuntime.addShutdownHook(shutdownHook)

      def cleanup(): Unit = {
        delete(tmpCache)
        delete(tmpFetchCache)
        Runtime.getRuntime.removeShutdownHook(shutdownHook)
      }

      def artifacts() =
        Fetch()
          .noMirrors
          .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
          .withCache(
            FileCache()
              .withLocation(tmpCache.toFile)
          )
          .withFetchCache(tmpFetchCache.toFile)
          .future()

      val artifacts0 = await(artifacts())

      val pomCount = remove(tmpCache)(_.endsWith(".pom"))
      val expectedPomCount = 18
      assert(pomCount == expectedPomCount)

      val artifacts1 = await(artifacts())

      assert(artifacts0 == artifacts1)

      val pomCount1 = remove(tmpCache)(_.endsWith(".pom"))
      val expectedPomCount1 = 0 // no POM must have been downloaded, artifact list read directly from the fetch cache
      assert(pomCount1 == expectedPomCount1)


      artifacts1(10).delete()

      val artifacts2 = await(artifacts())

      assert(artifacts0 == artifacts2)

      val pomCount2 = remove(tmpCache)(_.endsWith(".pom"))
      val expectedPomCount2 = 18 // POM must have been downloaded again, as the artifact list in cache was invalid
      assert(pomCount2 == expectedPomCount2)

      cleanup()
    }

    // TODO Find a way to add a test that changing stuff (snapshots) aren't cached

  }

}
