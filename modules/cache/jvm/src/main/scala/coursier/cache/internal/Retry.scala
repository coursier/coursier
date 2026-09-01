package coursier.cache.internal

import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit.MILLISECONDS

import scala.concurrent.duration.{Duration, DurationInt, FiniteDuration}
import scala.annotation.tailrec

/** Retries around the blocking parts of the cache.
  *
  * Three rather different things share this loop, and they want different delays, and different
  * budgets:
  *
  *   - an attempt that *failed* (an SSL error, a 503, a read timeout, …) is worth backing off from,
  *     exponentially, up to `maxDelay`, for `count` attempts;
  *   - an attempt that has *no answer yet* - typically one waiting on a download another thread of
  *     this JVM has in flight - only needs to be polled again, so its delay is kept under
  *     `maxPollDelay`. Backing off exponentially there means sleeping through the moment the
  *     artifact lands, and then some: an artifact that took 11 s to arrive used to leave the
  *     threads waiting on it asleep for 21 s, and a download that stalled for minutes left them
  *     asleep for hours;
  *   - an attempt that was *throttled* has not failed at all: the server is working, and asking us
  *     to come back later. Spending the attempts kept for real failures on it means giving up on a
  *     server that told us exactly how to succeed - with the default backoff, five attempts at 10,
  *     20, 40, 80 and 160 ms are gone a third of a second in, where a single `sleep 2` would have
  *     worked. So it doesn't touch `count`, and is bounded by `maxThrottleWait` of wall clock
  *     instead.
  */
final case class Retry(
  count: Int,
  initialDelay: FiniteDuration,
  delayMultiplier: Double,
  maxDelay: Option[FiniteDuration] = None,
  maxPollDelay: Option[FiniteDuration] = None,
  maxThrottleWait: Option[FiniteDuration] = None
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
    retryOpt0(f)(catchEx.andThen(_ => Retry.Failed(None)))

  def retryOpt0[T](f: => Option[T])(catchEx: PartialFunction[Throwable, Retry.Outcome]): T = {

    @tailrec
    def loop(
      attempt: Int,
      failureDelay: FiniteDuration,
      pollDelay: FiniteDuration,
      throttleDeadline: Option[Long]
    ): T = {
      val res: Either[Retry.Attempt, T] =
        if (Downloader.throwExceptions) f.toRight(Retry.Attempt(Retry.NoAnswerYet, null))
        else
          try f.toRight(Retry.Attempt(Retry.NoAnswerYet, null))
          catch {
            case t: Throwable if catchEx.isDefinedAt(t) => Left(Retry.Attempt(catchEx(t), t))
          }
      res match {
        case Right(res0) =>
          res0
        case Left(Retry.Attempt(Retry.NoAnswerYet, _)) =>
          // nothing failed, there is only something to wait for, so the attempt count is left
          // alone: polling shouldn't eat the budget meant for actual failures
          Thread.sleep(pollDelay.toMillis)
          loop(attempt, failureDelay, next(pollDelay, maxPollDelay), throttleDeadline)
        case Left(Retry.Attempt(Retry.Failed(forcedDelayOpt), ex)) =>
          if (attempt >= count) throw ex
          Thread.sleep(forcedDelayOpt.getOrElse(failureDelay).toMillis)
          loop(attempt + 1, next(failureDelay, maxDelay), pollDelay, throttleDeadline)
        case Left(Retry.Attempt(Retry.Throttled(delayOpt), ex)) =>
          // the attempt count is left alone here too - see the class doc
          val deadline =
            throttleDeadline.orElse(maxThrottleWait.map(System.nanoTime() + _.toNanos))
          val delay = Retry.jittered(delayOpt.getOrElse(Duration.Zero))
          // give up rather than sleep past the budget, so that we never wait longer than we said
          // we would, and never come back sooner than the server asked either
          if (deadline.exists(System.nanoTime() + delay.toNanos > _)) throw ex
          Thread.sleep(delay.toMillis)
          loop(attempt, failureDelay, pollDelay, deadline)
        case Left(Retry.Attempt(Retry.GiveUp, ex)) =>
          throw ex
      }
    }

    loop(1, capped(initialDelay, maxDelay), capped(initialDelay, maxPollDelay), None)
  }

}

object Retry {

  private[internal] sealed abstract class Outcome

  /** Nothing failed, there is only something to wait for */
  private[internal] case object NoAnswerYet extends Outcome

  /** The attempt failed, and is worth backing off from - for `count` attempts */
  private[internal] final case class Failed(forcedDelay: Option[FiniteDuration]) extends Outcome

  /** The attempt was turned away by a working server, which we should come back to in `delay` */
  private[internal] final case class Throttled(delay: Option[FiniteDuration]) extends Outcome

  /** The attempt is not worth making again, however much budget is left */
  private[internal] case object GiveUp extends Outcome

  /** What one attempt came back with
    *
    * `exOrNull` is the exception the outcome was read from, and is only null for `NoAnswerYet`,
    * which is the one outcome that doesn't come from one - and the one branch that never rethrows.
    */
  private final case class Attempt(outcome: Outcome, exOrNull: Throwable)

  /** How much jitter to add to a delay at most */
  private def maxJitter = 1.second

  /** Spreads out the threads that were all told to come back at the same moment
    *
    * `Retry-After` is usually the same value for every request in flight, so honouring it to the
    * millisecond has all of them arrive together and trip the limit again. Note this only ever
    * *adds*: coming back before the server said to is what gets a rate limit window extended.
    */
  private def jittered(delay: FiniteDuration): FiniteDuration =
    if (delay <= Duration.Zero) delay
    else {
      val spread = delay.min(maxJitter).toMillis
      delay + FiniteDuration(ThreadLocalRandom.current().nextLong(spread + 1L), MILLISECONDS)
    }
}
