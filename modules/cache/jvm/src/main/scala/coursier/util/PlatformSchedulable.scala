package coursier.util
import java.util.concurrent.ExecutorService

trait PlatformSchedulable[F[_]] {
  def schedule[A](pool: ExecutorService)(f: => A): F[A]
}
