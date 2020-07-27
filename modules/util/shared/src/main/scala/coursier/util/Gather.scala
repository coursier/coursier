package coursier.util

import simulacrum._

@typeclass trait Gather[F[_]] extends Monad[F] {
  def gather[A](elems: Seq[F[A]]): F[Seq[A]]
}
