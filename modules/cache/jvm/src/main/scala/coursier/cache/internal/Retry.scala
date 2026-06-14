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
  def retryOpt[T](f: => Option[T])(catchEx: PartialFunction[Throwable, Unit]): T =
    retryOpt0(f)(catchEx.andThen(_ => None))

  def retryOpt0[T](f: => Option[T])(catchEx: PartialFunction[Throwable, Option[FiniteDuration]])
    : T = {

    @tailrec
    def loop(attempt: Int, delay: FiniteDuration): T = {
      val resOpt =
        if (attempt >= count || Downloader.throwExceptions) f.toRight(None)
        else
          try f.toRight(None)
          catch catchEx.andThen(delayOpt => Left(delayOpt))
      resOpt match {
        case Right(res)           => res
        case Left(forcedDelayOpt) =>
          Thread.sleep(forcedDelayOpt.getOrElse(delay).toMillis)
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
