package coursier.interop

import java.util.concurrent.ExecutorService

import _root_.cats.effect.IO
import _root_.cats.instances.vector._
import _root_.cats.syntax.apply._
import coursier.util.Schedulable

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

abstract class PlatformCatsImplicits {

  // wish cs wasn't needed here (parSequence in gather needs it)
  implicit def catsIOSchedulable(implicit cs: _root_.cats.effect.ContextShift[IO]): Schedulable[IO] =
    new Schedulable[IO] {
      def point[A](a: A) =
        IO.pure(a)
      def delay[A](a: => A) =
        IO(a)
      def handle[A](a: IO[A])(f: PartialFunction[Throwable, A]) =
        a.handleErrorWith { e =>
          f.lift(e).fold[IO[A]](IO.raiseError(e))(IO.pure)
        }
      def schedule[A](pool: ExecutorService)(f: => A) = {

        val ec0 = pool match {
          case eces: ExecutionContextExecutorService => eces
          case _ => ExecutionContext.fromExecutorService(pool) // FIXME Is this instantiation costly? Cache it?
        }

        IO.shift(ec0) *> IO(f)
      }

      def gather[A](elems: Seq[IO[A]]) =
        _root_.cats.Parallel.parSequence(elems.toVector).map(_.toSeq)

      def bind[A, B](elem: IO[A])(f: A => IO[B]) =
        elem.flatMap(f)
    }

}
