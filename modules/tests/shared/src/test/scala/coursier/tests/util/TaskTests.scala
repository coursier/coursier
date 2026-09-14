package coursier.tests.util

import coursier.util.Task
import utest._

import java.util.concurrent.atomic.AtomicInteger

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.Duration

object TaskTests extends TestSuite {

  val tests = Tests {
    test("tailRecM") {
      import ExecutionContext.Implicits.global

      def countTo(i: Int): Task[Int] =
        Task.tailRecM(0) {
          case x if x >= i => Task.delay(Right(i))
          case toosmall    => Task.delay(Left(toosmall + 1))
        }
      countTo(500000).map(_ == 500000).future().map(assert(_))
    }

    test("memoize") {
      import ExecutionContext.Implicits.global

      val count = new AtomicInteger
      val task  = Task.delay(count.incrementAndGet()).memoize

      // not run until asked to
      assert(count.get() == 0)

      Task.gather.gather(Seq.fill(10)(task)).future().map { results =>
        assert(results == Seq.fill(10)(1))
        assert(count.get() == 1)
      }
    }

    test("memoize failure") {
      import ExecutionContext.Implicits.global

      val count = new AtomicInteger
      val task = Task.delay {
        count.incrementAndGet()
        sys.error("nope")
      }.memoize

      task.attempt.flatMap(_ => task.attempt).future().map { res =>
        assert(res.left.exists(_.getMessage == "nope"))
        assert(count.get() == 1)
      }
    }
  }
}
