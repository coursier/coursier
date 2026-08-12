package coursier.cache.internal

import scala.concurrent.duration.FiniteDuration
import scala.annotation.tailrec

/** Retries around the blocking parts of the cache.
  *
  * Two rather different things share this loop, and they want different delays:
  *
  *   - an attempt that *failed* (an SSL error, a 503, a read timeout, …) is worth backing off from,
  *     exponentially, up to `maxDelay`;
  *   - an attempt that has *no answer yet* - typically one waiting on a download another thread of
  *     this JVM has in flight - only needs to be polled again, so its delay is kept under
  *     `maxPollDelay`. Backing off exponentially there means sleeping through the moment the
  *     artifact lands, and then some: an artifact that took 11 s to arrive used to leave the
  *     threads waiting on it asleep for 21 s, and a download that stalled for minutes left them
  *     asleep for hours.
  */
final case class Retry(
  count: Int,
  initialDelay: FiniteDuration,
  delayMultiplier: Double,
  maxDelay: Option[FiniteDuration] = None,
  maxPollDelay: Option[FiniteDuration] = None
) {

  /** The delay for the attempt after this one, kept under `max`
    *
    * Capping as we go, rather than only when sleeping, also keeps the multiplication from
    * eventually overflowing what a `FiniteDuration` can hold.
    */
  private def next(delay: FiniteDuration, max: Option[FiniteDuration]): FiniteDuration =
    capped(
      delayMultiplier * delay match {
        case f: FiniteDuration => f
        case _                 => delay // should not happen
      },
      max
    )

  private def capped(delay: FiniteDuration, max: Option[FiniteDuration]): FiniteDuration =
    max.fold(delay)(_.min(delay))

  def retry[T](f: => T)(catchEx: PartialFunction[Throwable, Unit]): T =
    retryOpt(Some(f))(catchEx)

  // This may try more than retry times, if f returns None too many times
  def retryOpt[T](f: => Option[T])(catchEx: PartialFunction[Throwable, Unit]): T =
    retryOpt0(f)(catchEx.andThen(_ => None))

  def retryOpt0[T](f: => Option[T])(catchEx: PartialFunction[Throwable, Option[FiniteDuration]])
    : T = {

    @tailrec
    def loop(attempt: Int, failureDelay: FiniteDuration, pollDelay: FiniteDuration): T = {
      val res: Either[Retry.Outcome, T] =
        if (attempt >= count || Downloader.throwExceptions) f.toRight(Retry.NoAnswerYet)
        else
          try f.toRight(Retry.NoAnswerYet)
          catch catchEx.andThen(delayOpt => Left(Retry.Failed(delayOpt)))
      res match {
        case Right(res0) =>
          res0
        case Left(Retry.NoAnswerYet) =>
          // nothing failed, there is only something to wait for, so the attempt count is left
          // alone: polling shouldn't eat the budget meant for actual failures
          Thread.sleep(pollDelay.toMillis)
          loop(attempt, failureDelay, next(pollDelay, maxPollDelay))
        case Left(Retry.Failed(forcedDelayOpt)) =>
          Thread.sleep(forcedDelayOpt.getOrElse(failureDelay).toMillis)
          loop(attempt + 1, next(failureDelay, maxDelay), pollDelay)
      }
    }

    loop(1, capped(initialDelay, maxDelay), capped(initialDelay, maxPollDelay))
  }

}

object Retry {
  private[internal] sealed abstract class Outcome
  private[internal] case object NoAnswerYet                                      extends Outcome
  private[internal] final case class Failed(forcedDelay: Option[FiniteDuration]) extends Outcome
}
