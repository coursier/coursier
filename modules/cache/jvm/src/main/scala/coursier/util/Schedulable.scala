package coursier.util

import java.util.concurrent.{ExecutorService, Executors, ThreadFactory}

import scala.util.control.NonFatal

trait Schedulable[F[_]] extends Gather[F] {
  def delay[A](a: => A): F[A]
  def handle[A](a: F[A])(f: PartialFunction[Throwable, A]): F[A]
  def schedule[A](pool: ExecutorService)(f: => A): F[A]
  def fromAttempt[A](a: Either[Throwable, A]): F[A]
}

object Schedulable {

  def apply[F[_]](implicit schedulable: Schedulable[F]): Schedulable[F] =
    schedulable

  lazy val defaultThreadPool =
    fixedThreadPool(4 max Runtime.getRuntime.availableProcessors())

  def fixedThreadPool(size: Int): ExecutorService =
    Executors.newFixedThreadPool(
      size,
      // from scalaz.concurrent.Strategy.DefaultDaemonThreadFactory
      new ThreadFactory {
        val defaultThreadFactory = Executors.defaultThreadFactory()
        def newThread(r: Runnable) = {
          val t = defaultThreadFactory.newThread(r)
          t.setDaemon(true)
          t
        }
      }
    )

}
