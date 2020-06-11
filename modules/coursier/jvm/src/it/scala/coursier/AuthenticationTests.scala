package coursier

import java.io.File
import java.net.URI
import java.nio.file.{Files, Path}

import coursier.cache.FileCache
import coursier.credentials.{DirectCredentials, FileCredentials}
import coursier.parse.CredentialsParser
import utest._

object AuthenticationTests extends TestSuite {

  private val testRepo = Option(System.getenv("TEST_REPOSITORY")).getOrElse(sys.error("TEST_REPOSITORY not set"))
  private val user = Option(System.getenv("TEST_REPOSITORY_USER")).getOrElse(sys.error("TEST_REPOSITORY_USER not set"))
  private val password = Option(System.getenv("TEST_REPOSITORY_PASSWORD")).getOrElse(sys.error("TEST_REPOSITORY_PASSWORD not set"))

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

  private def testCredentials(credentials: DirectCredentials): Unit = {
    val result = withTmpDir { dir =>
      Resolve()
        .noMirrors
        .withRepositories(Seq(
          MavenRepository(testRepo),
          Repositories.central
        ))
        .addDependencies(dep"com.abc:test:0.1".withTransitive(false))
        .withCache(
          FileCache()
            .noCredentials
            .withLocation(dir.toFile)
            .addCredentials(credentials)
        )
        .run()
    }
    val modules = result.minDependencies.map(_.module)
    val expectedModules = Set(mod"com.abc:test")
    assert(modules == expectedModules)
  }

  val tests = Tests {

    * - {
      testCredentials {
        DirectCredentials()
          .withHost(testHost)
          .withUsername(user)
          .withPassword(password)
          .withMatchHost(true)
          .withHttpsOnly(false)
      }
    }

    * - {
      val credentialsStr = s"$testHost $user:$password"
      val credentials = CredentialsParser.parse(credentialsStr) match {
        case Left(error) => sys.error(s"Error parsing credentials: $error")
        case Right(c) => c
      }
      testCredentials(credentials)
    }

    * - {
      val content =
       s"""foo.username=$user
          |foo.password=$password
          |foo.host=$testHost
          |""".stripMargin
      val allCredentials = FileCredentials.parse(content, s"'$content'")
      assert(allCredentials.length == 1)
      val credentials = allCredentials.head
      testCredentials(credentials)
    }

  }

}
