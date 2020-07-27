package coursier.util

import coursier.util.Monad.ops._

final case class EitherT[F[_], L, R](run: F[Either[L, R]]) {

  def map[S](f: R => S)(implicit M: Monad[F]): EitherT[F, L, S] =
    EitherT(
      run.map(_.map(f))
    )

  def flatMap[S](f: R => EitherT[F, L, S])(implicit M: Monad[F]): EitherT[F, L, S] =
    EitherT(
      run.flatMap {
        case Left(l) =>
          M.point(Left(l))
        case Right(r) =>
          f(r).run
      }
    )

  def leftMap[M](f: L => M)(implicit M: Monad[F]): EitherT[F, M, R] =
    EitherT(
      run.map(_.left.map(f))
    )

  def leftFlatMap[S](f: L => EitherT[F, S, R])(implicit M: Monad[F]): EitherT[F, S, R] =
    EitherT(
      run.flatMap {
        case Left(l) =>
          f(l).run
        case Right(r) =>
          M.point(Right(r))
      }
    )

  def orElse(other: => EitherT[F, L, R])(implicit M: Monad[F]): EitherT[F, L, R] =
    EitherT(
      run.flatMap {
        case Left(_) =>
          other.run
        case Right(r) =>
          M.point(Right(r))
      }
    )

}

object EitherT {

  def point[F[_], L, R](r: R)(implicit M: Monad[F]): EitherT[F, L, R] =
    EitherT(M.point(Right(r)))

  def fromEither[F[_]]: FromEither[F] =
    new FromEither[F]

  final class FromEither[F[_]] {
    def apply[L, R](either: Either[L, R])(implicit M: Monad[F]): EitherT[F, L, R] =
      EitherT(M.point(either))
  }

}
