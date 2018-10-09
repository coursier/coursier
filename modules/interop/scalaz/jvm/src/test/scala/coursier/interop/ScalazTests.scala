package coursier.interop

import coursier.{Module, moduleNameString, organizationString}
import coursier.interop.scalaz._
import coursier.test.compatibility.executionContext
import coursier.test.{TestRunner, compatibility}
import _root_.scalaz.concurrent.{Task => ScalazTask}
import coursier.test.util.ToFuture
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

    'spark - {
      * - runner.resolutionCheck(
        Module(org"org.apache.spark", name"spark-core_2.11"),
        "1.3.1",
        profiles = Some(Set("hadoop-2.2"))
      )

      'scala210 - runner.resolutionCheck(
        Module(org"org.apache.spark", name"spark-core_2.10"),
        "2.1.1",
        profiles = Some(Set("hadoop-2.6", "scala-2.10", "!scala-2.11"))
      )
    }

    'argonautShapeless - {
      runner.resolutionCheck(
        Module(org"com.github.alexarchambault", name"argonaut-shapeless_6.1_2.11"),
        "0.2.0"
      )
    }

  }

}
