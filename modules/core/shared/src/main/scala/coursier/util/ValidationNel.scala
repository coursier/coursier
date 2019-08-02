package coursier.util

// not covariant because scala.:: isn't (and is there a point in being covariant in R but not L?)
final case class ValidationNel[L, R](either: Either[::[L], R]) {
  def isSuccess: Boolean =
    either.isRight
  def map[S](f: R => S): ValidationNel[L, S] =
    ValidationNel(either.right.map(f))
  def flatMap[S](f: R => ValidationNel[L, S]): ValidationNel[L, S] =
    ValidationNel(either.right.flatMap(r => f(r).either))

  def zip[R1](other: ValidationNel[L, R1]): ValidationNel[L, (R, R1)] =
    (either, other.either) match {
      case (Right(ra), Right(rb)) =>
        ValidationNel.success((ra, rb))
      case _ =>
        val errs = either.left.getOrElse(Nil) ::: other.either.left.getOrElse(Nil)
        errs match {
          case h :: t =>
            ValidationNel.failures(h, t: _*)
          case Nil =>
            sys.error("Can't possibly happen")
        }
    }

  def zip[R1, R2](second: ValidationNel[L, R1], third: ValidationNel[L, R2]): ValidationNel[L, (R, R1, R2)] =
    (either, second.either, third.either) match {
      case (Right(ra), Right(rb), Right(rc)) =>
        ValidationNel.success((ra, rb, rc))
      case _ =>
        val errs = either.left.getOrElse(Nil) ::: second.either.left.getOrElse(Nil) ::: third.either.left.getOrElse(Nil)
        errs match {
          case h :: t =>
            ValidationNel.failures(h, t: _*)
          case Nil =>
            sys.error("Can't possibly happen")
        }
    }
}

object ValidationNel {
  def fromEither[L, R](either: Either[L, R]): ValidationNel[L, R] =
    ValidationNel(either.left.map(l => ::(l, Nil)))
  def success[L]: SuccessBuilder[L] =
    new SuccessBuilder
  def failure[R]: FailureBuilder[R] =
    new FailureBuilder
  def failures[R]: FailuresBuilder[R] =
    new FailuresBuilder

  final class SuccessBuilder[L] {
    def apply[R](r: R): ValidationNel[L, R] =
      ValidationNel(Right(r))
  }
  final class FailureBuilder[R] {
    def apply[L](l: L): ValidationNel[L, R] =
      ValidationNel(Left(::(l, Nil)))
  }
  final class FailuresBuilder[R] {
    def apply[L](l: L, rest: L*): ValidationNel[L, R] =
      ValidationNel(Left(::(l, rest.toList)))
  }
}
