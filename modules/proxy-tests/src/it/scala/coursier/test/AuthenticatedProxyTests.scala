package coursier.test

import java.net.InetSocketAddress

import coursier.{moduleString, Repositories}
import coursier.test.compatibility._
import coursier.util.Task
import utest._

import scala.async.Async.{async, await}

object AuthenticatedProxyTests extends TestSuite {

  private val okRepo = DockerServer(
    "bahamat/authenticated-proxy@sha256:568c759ac687f93d606866fbb397f39fe1350187b95e648376b971e9d7596e75",
    "",
    80 -> 9083,
    healthCheck = false
  )
  private val nopeRepo = DockerServer(
    "bahamat/authenticated-proxy@sha256:568c759ac687f93d606866fbb397f39fe1350187b95e648376b971e9d7596e75",
    "",
    80 -> 9084,
    healthCheck = false
  )

  override def utestAfterAll(): Unit = {
    okRepo.shutdown()
    nopeRepo.shutdown()
  }

  private val okProxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress("localhost", 9083))
  private val nopeProxy = new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress("localhost", 9084))

  private lazy val okRunner = new coursier.test.TestRunner(
    artifact = artifactWithProxy[Task](okProxy),
    repositories = Seq(Repositories.central)
  )
  private lazy val nopeRunner = new coursier.test.TestRunner(
    artifact = artifactWithProxy[Task](nopeProxy),
    repositories = Seq(Repositories.central)
  )

  val tests = Tests {

    test("simple") - async {

      val res = await {
        nopeRunner.resolutionCheck(
          mod"com.github.alexarchambault:argonaut-shapeless_6.1_2.11",
          "0.2.0"
        ).map(Right(_)).recover { case t: Throwable => Left(t) }
      }
      assert(res.isLeft)
      val message = res.left.get.getCause.getMessage
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
