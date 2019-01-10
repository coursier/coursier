package coursier.interop

import _root_.cats.effect.IO
import coursier.moduleString
import coursier.interop.cats.{coursierGatherFromCats => _, _}
import coursier.test.compatibility.executionContext
import coursier.test.{TestRunner, compatibility}
import coursier.test.util.ToFuture
import utest._

import scala.concurrent.{ExecutionContext, Future}

object CatsTests extends TestSuite {

  private implicit val cs = _root_.cats.effect.IO.contextShift(executionContext)

  // few basic tests from CentralTests, to ensure everything is wired correctly with cats.effect.IO

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

    'spark - {
      * - runner.resolutionCheck(
        mod"org.apache.spark:spark-core_2.11",
        "1.3.1",
        profiles = Some(Set("hadoop-2.2"))
      )

      'scala210 - runner.resolutionCheck(
        mod"org.apache.spark:spark-core_2.10",
        "2.1.1",
        profiles = Some(Set("hadoop-2.6", "scala-2.10", "!scala-2.11"))
      )
    }

    'argonautShapeless - {
      runner.resolutionCheck(
        mod"com.github.alexarchambault:argonaut-shapeless_6.1_2.11",
        "0.2.0"
      )
    }

  }

}
