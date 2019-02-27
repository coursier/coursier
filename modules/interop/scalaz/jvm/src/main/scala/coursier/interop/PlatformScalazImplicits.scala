package coursier.interop

import java.util.concurrent.ExecutorService

import coursier.util.Sync
import _root_.scalaz.concurrent.{Task => ScalazTask}

abstract class PlatformScalazImplicits {

  implicit val scalazTaskSync: Sync[ScalazTask] =
    new Sync[ScalazTask] {
      def point[A](a: A) =
        ScalazTask.point(a)
      def delay[A](a: => A): ScalazTask[A] =
        ScalazTask.delay(a)
      override def fromAttempt[A](a: Either[Throwable, A]): ScalazTask[A] =
        a match {
          case Left(t) => ScalazTask.fail(t)
          case Right(x) => ScalazTask.now(x)
        }
      def handle[A](a: ScalazTask[A])(f: PartialFunction[Throwable, A]) =
        a.handle(f)
      def schedule[A](pool: ExecutorService)(f: => A) =
        ScalazTask(f)(pool)

      def gather[A](elems: Seq[ScalazTask[A]]) =
        ScalazTask.taskInstance.gather(elems)

      def bind[A, B](elem: ScalazTask[A])(f: A => ScalazTask[B]) =
        ScalazTask.taskInstance.bind(elem)(f)
    }

}
