package coursier

import java.io.File
import java.net.URI
import java.nio.file.{Files, Path}

import coursier.cache.FileCache
import coursier.credentials.DirectCredentials
import utest._

object AuthenticationTests extends TestSuite {

  private val testRepo = sys.env.getOrElse("TEST_REPOSITORY", sys.error("TEST_REPOSITORY not set"))
  private val user = sys.env.getOrElse("TEST_REPOSITORY_USER", sys.error("TEST_REPOSITORY_USER not set"))
  private val password = sys.env.getOrElse("TEST_REPOSITORY_PASSWORD", sys.error("TEST_REPOSITORY_PASSWORD not set"))

  private val testHost = new URI(testRepo).getHost

  private def deleteRecursive(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles().foreach(deleteRecursive)
    f.delete()
  }

  private def withTmpDir[T](f: Path => T): T = {
    val dir = Files.createTempDirectory("coursier-test")
    try f(dir)
    finally {
      deleteRecursive(dir.toFile)
    }
  }

  val tests = Tests {

    * - {
      val result = withTmpDir { dir =>
        Resolve()
          .withRepositories(Seq(
            MavenRepository(testRepo),
            Repositories.central
          ))
          .addDependencies(dep"com.abc:test:0.1".copy(transitive = false))
          .withCache(
            FileCache()
              .withLocation(dir.toFile)
              .addCredentials(
                DirectCredentials()
                  .withHost(testHost)
                  .withUsername(user)
                  .withPassword(password)
              )
          )
          .run()
      }
      val modules = result.minDependencies.map(_.module)
      val expectedModules = Set(mod"com.abc:test")
      assert(modules == expectedModules)
    }

  }

}
