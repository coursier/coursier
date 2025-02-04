package coursier.tests

import java.net.InetSocketAddress

import coursier.Repositories
import coursier.tests.compatibility._
import coursier.util.StringInterpolators._
import coursier.util.Task
import utest._

import scala.async.Async.{async, await}
import scala.util.Properties.isWin

object AuthenticatedProxyTests extends TestSuite {

  private lazy val okRepo = DockerServer(
    "bahamat/authenticated-proxy@sha256:568c759ac687f93d606866fbb397f39fe1350187b95e648376b971e9d7596e75",
    "",
    80 -> 9083,
    healthCheck = false
  )
  private lazy val nopeRepo = DockerServer(
    "bahamat/authenticated-proxy@sha256:568c759ac687f93d606866fbb397f39fe1350187b95e648376b971e9d7596e75",
    "",
    80 -> 9084,
    healthCheck = false
  )

  if (!isWin) {
    // eagerly create repos
    okRepo
    nopeRepo
  }

  override def utestAfterAll(): Unit =
    if (!isWin) {
      okRepo.shutdown()
      nopeRepo.shutdown()
    }

  private lazy val okProxy =
    new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress("localhost", 9083))
  private lazy val nopeProxy =
    new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress("localhost", 9084))

  private lazy val okRunner = new coursier.tests.TestRunner(
    artifact = artifactWithProxy[Task](okProxy),
    repositories = Seq(Repositories.central)
  )
  private lazy val nopeRunner = new coursier.tests.TestRunner(
    artifact = artifactWithProxy[Task](nopeProxy),
    repositories = Seq(Repositories.central)
  )

  def actualTests = Tests {

    test("simple") {
      async {

        val res = await {
          nopeRunner.resolutionCheck(
            mod"com.github.alexarchambault:argonaut-shapeless_6.1_2.11",
            "0.2.0"
          ).map(Right(_)).recover { case t: Throwable => Left(t) }
        }
        assert(res.isLeft)
        val message = res.swap.toOption.get.getCause.getMessage
        assert(message.contains("407 Proxy Authentication Required"))

        await {
          okRunner.resolutionCheck(
            mod"com.github.alexarchambault:argonaut-shapeless_6.1_2.11",
            "0.2.0"
          )
        }
      }
    }

  }

  val tests: Tests =
    if (isWin)
      Tests {
        test("disabled") {
          "disabled"
        }
      }
    else
      actualTests
}
