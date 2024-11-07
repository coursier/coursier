package coursier.interop

import _root_.cats.effect.IO
import _root_.cats.effect.unsafe.IORuntime
import coursier.interop.cats.{coursierGatherFromCats => _, _}
import coursier.tests.compatibility.executionContext
import coursier.tests.{TestRunner, compatibility}
import coursier.tests.util.ToFuture
import coursier.util.StringInterpolators._
import utest._

import scala.concurrent.{ExecutionContext, Future}

object CatsTests extends TestSuite {

  // few basic tests from CentralTests, to ensure everything is wired correctly with cats.effect.IO

  private implicit def ioRuntime: IORuntime = IORuntime.global
  private implicit val ioToFuture: ToFuture[IO] =
    new ToFuture[IO] {
      def toFuture[T](ec: ExecutionContext, f: IO[T]) =
        Future(())
          .flatMap(_ => f.unsafeToFuture())
    }

  private lazy val runner = new TestRunner(
    artifact = compatibility.artifact[IO]
  )

  val tests = Tests {

    test("spark") {
      test - runner.resolutionCheck(
        mod"org.apache.spark:spark-core_2.11",
        "1.3.1",
        profiles = Some(Set("hadoop-2.2", "!scala-2.10", "scala-2.11")),
        forceDepMgmtVersions = Some(true)
      )

      test("scala210") - runner.resolutionCheck(
        mod"org.apache.spark:spark-core_2.10",
        "2.1.1",
        profiles = Some(Set("hadoop-2.6", "scala-2.10", "!scala-2.11")),
        forceDepMgmtVersions = Some(true)
      )
    }

    test("argonautShapeless") {
      runner.resolutionCheck(
        mod"com.github.alexarchambault:argonaut-shapeless_6.1_2.11",
        "0.2.0"
      )
    }

  }

}
