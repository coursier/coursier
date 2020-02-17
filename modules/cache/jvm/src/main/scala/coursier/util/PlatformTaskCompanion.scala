package coursier.util

import java.util.concurrent.{ExecutorService, ScheduledExecutorService}

import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutorService, Future}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.Promise
import scala.util.Success

abstract class PlatformTaskCompanion { self =>

  def schedule[A](pool: ExecutorService)(f: => A): Task[A] = {

    val ec0 = pool match {
      case eces: ExecutionContextExecutorService => eces
      case _ => ExecutionContext.fromExecutorService(pool) // FIXME Is this instantiation costly? Cache it?
    }

    Task(_ => Future(f)(ec0))
  }

  def completeAfter(pool: ScheduledExecutorService, duration: FiniteDuration): Task[Unit] =
    Task.delay {
      val p = Promise[Unit]()
      val runnable =
        new Runnable {
          def run(): Unit =
            p.complete(Success(()))
        }
      pool.schedule(runnable, duration.length, duration.unit)
      Task(_ => p.future)
    }.flatMap(identity)

  implicit val sync: Sync[Task] =
    new TaskSync {
      def schedule[A](pool: ExecutorService)(f: => A) = self.schedule(pool)(f)
    }

  implicit class PlatformTaskOps[T](private val task: Task[T]) {
    def unsafeRun()(implicit ec: ExecutionContext): T =
      Await.result(task.future(), Duration.Inf)
  }

}
