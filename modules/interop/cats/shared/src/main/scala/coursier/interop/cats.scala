package coursier.interop

import _root_.cats.instances.vector._
import coursier.util.{Gather, Monad}

object cats extends LowPriorityCatsImplicits {

  implicit def coursierMonadFromCats[F[_]](implicit M: _root_.cats.Monad[F]): Monad[F] =
    new Monad[F] {
      def point[A](a: A) = M.pure(a)
      def bind[A, B](elem: F[A])(f: A => F[B]) = M.flatMap(elem)(f)
    }

}

abstract class LowPriorityCatsImplicits extends PlatformCatsImplicits {

  implicit def coursierGatherFromCats[F[_], F0[_]](implicit N: _root_.cats.Monad[F], cs: _root_.cats.Parallel.Aux[F, F0]): Gather[F] =
    new Gather[F] {
      def point[A](a: A) = N.pure(a)
      def bind[A, B](elem: F[A])(f: A => F[B]) = N.flatMap(elem)(f)
      def gather[A](elems: Seq[F[A]]) = {
        N.map(_root_.cats.Parallel.parSequence(elems.toVector))(_.toSeq)
      }
    }

}
