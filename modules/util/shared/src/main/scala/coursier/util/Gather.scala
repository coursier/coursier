package coursier.util

trait Gather[F[_]] extends Monad[F] {
  def gather[A](elems: Seq[F[A]]): F[Seq[A]]
}

object Gather {
  def apply[F[_]](implicit instance: Gather[F]): Gather[F] = instance

  trait Ops[F[_], A] {
    def typeClassInstance: Gather[F]
    def self: F[A]
  }

  trait ToGatherOps {
    implicit def toGatherOps[F[_], A](target: F[A])(implicit tc: Gather[F]): Ops[F, A] =
      new Ops[F, A] {
        val self              = target
        val typeClassInstance = tc
      }
  }

  object nonInheritedOps extends ToGatherOps

  trait AllOps[F[_], A] extends Monad.AllOps[F, A] with Ops[F, A] {
    def typeClassInstance: Gather[F]
  }

  object ops {
    implicit def toAllGatherOps[F[_], A](target: F[A])(implicit tc: Gather[F]): AllOps[F, A] =
      new AllOps[F, A] {
        val self              = target
        val typeClassInstance = tc
      }
  }
}
