package coursier.cache.internal

import scala.concurrent.duration.FiniteDuration
import scala.annotation.tailrec

final case class Retry(
  count: Int,
  initialDelay: FiniteDuration,
  delayMultiplier: Double
) {

  def retry[T](f: => T)(catchEx: PartialFunction[Throwable, Unit]): T =
    retryOpt(Some(f))(catchEx)

  // This may try more than retry times, if f returns None too many times
  def retryOpt[T](f: => Option[T])(catchEx: PartialFunction[Throwable, Unit]): T = {

    @tailrec
    def loop(attempt: Int, delay: FiniteDuration): T = {
      val resOpt =
        if (attempt >= count || Downloader.throwExceptions) f
        else
          try f
          catch catchEx.andThen(_ => None)
      resOpt match {
        case Some(res) => res
        case None =>
          Thread.sleep(delay.toMillis)
          loop(
            attempt + 1,
            delayMultiplier * delay match {
              case f: FiniteDuration => f
              case _                 => delay // should not happen
            }
          )
      }
    }

    loop(1, initialDelay)
  }

}
