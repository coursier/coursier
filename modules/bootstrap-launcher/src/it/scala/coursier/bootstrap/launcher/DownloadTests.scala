package coursier.bootstrap.launcher

import utest._

import coursier.bootstrap.launcher.credentials.Credentials
import coursier.bootstrap.launcher.credentials.DirectCredentials
import coursier.paths.CachePath

import scala.jdk.CollectionConverters._
import java.nio.file.Files
import java.io.File
import java.nio.file.Path
import java.net.URL
import java.nio.file.Paths
import java.net.URI
import java.util.Collections

object DownloadTests extends TestSuite {

  private val testRepository = Option(System.getenv("TEST_REPOSITORY"))
    .orElse(sys.props.get("test.repository"))
    .getOrElse(sys.error("TEST_REPOSITORY not set"))
  private val testRepositoryUser = Option(System.getenv("TEST_REPOSITORY_USER"))
    .orElse(sys.props.get("test.repository.user"))
    .getOrElse(sys.error("TEST_REPOSITORY_USER not set"))
  private val testRepositoryPassword = Option(System.getenv("TEST_REPOSITORY_PASSWORD"))
    .orElse(sys.props.get("test.repository.password"))
    .getOrElse(sys.error("TEST_REPOSITORY_PASSWORD not set"))
  private val testRepositoryHost = new URI(testRepository).getHost

  private def deleteRecursive(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles().foreach(deleteRecursive)
    if (f.exists())
      f.delete()
  }

  private def withTmpDir[T](f: Path => T): T = {
    val dir = Files.createTempDirectory("coursier-test")
    val shutdownHook: Thread =
      new Thread {
        override def run() =
          deleteRecursive(dir.toFile)
      }
    Runtime.getRuntime.addShutdownHook(shutdownHook)
    try f(dir)
    finally {
      deleteRecursive(dir.toFile)
      Runtime.getRuntime.removeShutdownHook(shutdownHook)
    }
  }

  val tests = Tests {

    test("public URL") {
      withTmpDir { dir =>
        val remoteUrls = Seq(
          new URL("https://repo1.maven.org/maven2/com/chuusai/shapeless_2.12/2.3.3/shapeless_2.12-2.3.3.jar")
        )
        val download = new Download(1, dir.toFile, Collections.emptyList())
        val localUrls = download.getLocalURLs(remoteUrls.asJava).asScala
        assert(remoteUrls.size == localUrls.size)
        assert(localUrls
          .map(url => Paths.get(url.toURI).toFile)
          .foldLeft(true)(_ && _.exists))
      }
    }

    test ("private URL with credentials in the URL") {
      withTmpDir { dir =>
        val remoteUrls = Seq(
          new URL(s"http://$testRepositoryUser:$testRepositoryPassword@" + s"$testRepository/com/abc/test/0.1/test-0.1.pom".stripPrefix("http://"))
        )
        val download = new Download(1, dir.toFile, Collections.emptyList())
        val localUrls = download.getLocalURLs(remoteUrls.asJava).asScala
        assert(remoteUrls.size == localUrls.size)
        assert(localUrls
          .map(url => Paths.get(url.toURI).toFile)
          .foldLeft(true)(_ && _.exists))
      }
    }

    test ("priavte URL with configured credentials") {
      withTmpDir { dir =>
        val remoteUrls = Seq(
          new URL(s"$testRepository/com/abc/test/0.1/test-0.1.pom")
        )
        val download = new Download(1, dir.toFile, Collections.singletonList(
          new DirectCredentials(testRepositoryHost, testRepositoryUser, testRepositoryPassword).withMatchHost(true).withHttpsOnly(false)
        ))
        val localUrls = download.getLocalURLs(remoteUrls.asJava).asScala
        assert(remoteUrls.size == localUrls.size)
        assert(localUrls
          .map(url => Paths.get(url.toURI).toFile)
          .foldLeft(true)(_ && _.exists))
      }
    }

  }

}
