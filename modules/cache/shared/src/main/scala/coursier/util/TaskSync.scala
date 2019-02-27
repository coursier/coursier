package coursier.util
import scala.concurrent.Future

trait TaskSync extends Sync[Task] {
  def point[A](a: A) = Task.point(a)
  def bind[A, B](elem: Task[A])(f: A => Task[B]) =
    elem.flatMap(f)

  def gather[A](elems: Seq[Task[A]]) =
    Task(implicit ec => Future.sequence(elems.map(_.value(ec))))
  def delay[A](a: => A) = Task.delay(a)
  override def fromAttempt[A](a: Either[Throwable, A]): Task[A] =
    Task.fromEither(a)
  def handle[A](a: Task[A])(f: PartialFunction[Throwable, A]) =
    a.handle(f)
}
