package coursier.util
import java.util.concurrent.ExecutorService

trait PlatformSync[F[_]] {
  def schedule[A](pool: ExecutorService)(f: => A): F[A]
}
