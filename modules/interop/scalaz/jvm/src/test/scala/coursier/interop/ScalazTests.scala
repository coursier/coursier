package coursier.interop

import coursier.interop.scalaz.{coursierGatherFromScalaz => _, _}
import coursier.tests.compatibility.executionContext
import coursier.tests.{TestRunner, compatibility}
import coursier.util.StringInterpolators._
import _root_.scalaz.concurrent.{Task => ScalazTask}
import coursier.tests.util.ToFuture
import utest._

import scala.concurrent.{ExecutionContext, Promise}
import scala.util.{Failure, Success}

object ScalazTests extends TestSuite {

  // few basic tests from CentralTests, to ensure everything is wired correctly with scalaz.concurrent.Task

  private implicit val scalazTaskToFuture: ToFuture[ScalazTask] =
    new ToFuture[ScalazTask] {
      def toFuture[T](ec: ExecutionContext, f: ScalazTask[T]) = {
        val p = Promise[T]()
        f.unsafePerformAsync { res =>
          val res0 = res.fold(Failure(_), Success(_))
          p.complete(res0)
        }
        p.future
      }
    }

  private lazy val runner = new TestRunner(
    artifact = compatibility.artifact[ScalazTask]
  )

  val tests = Tests {

    test("spark") {
      test - runner.resolutionCheck(
        mod"org.apache.spark:spark-core_2.11",
        "1.3.1",
        profiles = Some(Set("hadoop-2.2", "!scala-2.10", "scala-2.11"))
      )

      test("scala210") - runner.resolutionCheck(
        mod"org.apache.spark:spark-core_2.10",
        "2.1.1",
        profiles = Some(Set("hadoop-2.6", "scala-2.10", "!scala-2.11"))
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
