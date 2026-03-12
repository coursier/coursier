package coursier.util

import scala.language.implicitConversions

trait Monad[F[_]] extends Serializable {
  def point[A](a: A): F[A]
  def bind[A, B](elem: F[A])(f: A => F[B]): F[B]

  def map[A, B](elem: F[A])(f: A => B): F[B] =
    bind(elem)(a => point(f(a)))
}

object Monad {
  def apply[F[_]](implicit instance: Monad[F]): Monad[F] = instance

  trait Ops[F[_], A] {
    def typeClassInstance: Monad[F]
    def self: F[A]
    def map[B](f: A => B): F[B]        = typeClassInstance.map(self)(f)
    def flatMap[B](f: A => F[B]): F[B] = typeClassInstance.bind(self)(f)
  }

  trait ToMonadOps {
    implicit def toMonadOps[F[_], A](target: F[A])(implicit tc: Monad[F]): Ops[F, A] =
      new Ops[F, A] {
        val self              = target
        val typeClassInstance = tc
      }
  }

  object nonInheritedOps extends ToMonadOps

  trait AllOps[F[_], A] extends Ops[F, A] {
    def typeClassInstance: Monad[F]
  }

  object ops {
    implicit def toAllMonadOps[F[_], A](target: F[A])(implicit tc: Monad[F]): AllOps[F, A] =
      new AllOps[F, A] {
        val self              = target
        val typeClassInstance = tc
      }
  }
}
