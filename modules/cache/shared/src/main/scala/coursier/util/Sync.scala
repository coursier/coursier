package coursier.util

trait Sync[F[_]] extends Gather[F] with PlatformSync[F] {
  def delay[A](a: => A): F[A]
  def handle[A](a: F[A])(f: PartialFunction[Throwable, A]): F[A]
  def fromAttempt[A](a: Either[Throwable, A]): F[A]

  def attempt[A](f: F[A]): F[Either[Throwable, A]] =
    handle(map(f)(Right(_): Either[Throwable, A])) {
      // in the case of Task, fatal errors are trapped anyway here, as it is backed by scala.concurrent.Future,
      // causing some Task to never complete…
      // (https://stackoverflow.com/questions/32641464/exception-causes-future-to-never-complete)
      case t: Throwable => Left(t)
    }
}

object Sync extends PlatformSyncCompanion {

  def apply[F[_]](implicit sync: Sync[F]): Sync[F] =
    sync

}
