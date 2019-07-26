package coursier.interop

import java.util.concurrent.ExecutorService

import _root_.cats.instances.vector._
import _root_.cats.syntax.all._
import coursier.util.Sync

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

abstract class PlatformCatsImplicits {

  implicit def coursierSyncFromCats[F[_], F0[_]](implicit N: _root_.cats.effect.Sync[F], par: _root_.cats.Parallel[F, F0], cs: _root_.cats.effect.ContextShift[F]): Sync[F] =
    new Sync[F] {
      def point[A](a: A): F[A] =
        a.pure[F]
      def delay[A](a: => A): F[A] =
        N.delay(a)
      override def fromAttempt[A](a: Either[Throwable, A]): F[A] =
        N.fromEither(a)
      def handle[A](a: F[A])(f: PartialFunction[Throwable, A]): F[A] =
        a.recover(f)
      def schedule[A](pool: ExecutorService)(f: => A): F[A] = {
        val ec0 = pool match {
          case eces: ExecutionContextExecutorService => eces
          case _ => ExecutionContext.fromExecutorService(pool) // FIXME Is this instantiation costly? Cache it?
        }
        cs.evalOn(ec0)(N.delay(f))
      }

      def gather[A](elems: Seq[F[A]]): F[Seq[A]] =
        N.map(_root_.cats.Parallel.parSequence(elems.toVector))(_.toSeq)
      def bind[A, B](elem: F[A])(f: A => F[B]): F[B] =
        elem.flatMap(f)
    }

}
