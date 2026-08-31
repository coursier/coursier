package coursier.cache.internal

import coursier.cache.CacheDefaults

import java.net.URI
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit.MILLISECONDS

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Try

/** Keeps track, per host, of how long we were asked to hold off before sending requests again.
  *
  * A 429 is about the server, not about the artifact we happened to be asking for, and the thread
  * that gets one is rarely the only one talking to that host. Recording the pause here, rather than
  * only in the retry loop of the download that ran into it, lets the other threads - and the
  * requests that have not started yet - wait it out too. Otherwise each of them discovers the limit
  * on its own, and between them they keep up the very request rate the server just asked us to cut,
  * with `Retry-After` making sure they all come back at the same moment.
  *
  * Instances are safe to share between threads, and meant to be: a throttle no one else consults is
  * only an elaborate way of writing `Thread.sleep`.
  */
final class HostThrottle(
  initialDelay: FiniteDuration = CacheDefaults.throttleInitialDelay,
  maxDelay: Option[FiniteDuration] = CacheDefaults.throttleMaxDelay,
  delayMultiplier: Double = CacheDefaults.retryBackoffMultiplier,
  maxRetryAfter: Option[FiniteDuration] = CacheDefaults.maxHttpRetryAfter,
  clock: Clock = Clock.systemDefaultZone()
) {

  import HostThrottle._

  private val states = new ConcurrentHashMap[String, State]

  private def state(url: String): Option[State] =
    hostKey(url).map(key => states.computeIfAbsent(key, _ => new State(initialDelay)))

  /** Records that `url`'s host asked us to slow down
    *
    * `retryAfterOpt` is what its `Retry-After` header said, if it sent one. Either way the host's
    * own pause grows, and the one we observe is whichever of the two is longer: waiting longer than
    * asked is always safe, where coming back sooner is what gets a rate limit window extended. It
    * also keeps a server that answers `Retry-After: 0` - or none at all - from being retried as
    * fast as the loop can go.
    *
    * A rate limit is expressed per second or per minute, which is why our own pause starts around a
    * second rather than at the millisecond scale that suits a connection error: retrying one 10 ms
    * later is just another request for the limiter to reject, and several of them count rejected
    * requests toward the limit they are enforcing.
    *
    * Returns the pause when this 429 is what started it, and `None` when it only joined one already
    * under way - so that a burst of them, which is what a rate limit tends to produce, is worth
    * reporting once rather than once per thread that ran into it.
    */
  def rateLimited(url: String, retryAfterOpt: Option[FiniteDuration]): Option[FiniteDuration] =
    state(url).flatMap { state0 =>
      state0.synchronized {
        val ours = state0.delay
        state0.delay = capped(ours * delayMultiplier, maxDelay)
        // Holding off for less than the server asked is what we refuse to do, so a `Retry-After`
        // we are not willing to sit out is one to give up on rather than shorten.
        state0.tooLong = retryAfterOpt.exists(retryAfter => maxRetryAfter.exists(retryAfter > _))
        val delay = retryAfterOpt.fold(ours)(_.max(ours))
        val now   = clock.millis()
        val fresh = state0.notBefore <= now
        state0.notBefore = math.max(state0.notBefore, now + delay.toMillis)
        if (fresh) Some(delay) else None
      }
    }

  /** Whether a request can be sent to `url`'s host, and if not, for how long it cannot */
  def holdOff(url: String): HoldOff =
    hostKey(url).flatMap(key => Option(states.get(key))) match {
      case None => Clear
      case Some(state0) =>
        val left = state0.notBefore - clock.millis()
        if (left <= 0L) Clear
        else if (state0.tooLong) TooLong(FiniteDuration(left, MILLISECONDS))
        else Wait(FiniteDuration(left, MILLISECONDS))
    }

  /** Records that `url`'s host answered us normally, so the next pause we pick starts small again
    */
  def succeeded(url: String): Unit =
    for {
      key    <- hostKey(url)
      state0 <- Option(states.get(key))
    } state0.synchronized {
      state0.delay = initialDelay
      state0.tooLong = false
    }

  private def capped(delay: Duration, max: Option[FiniteDuration]): FiniteDuration = {
    val delay0 = delay match {
      case f: FiniteDuration => f
      case _                 => initialDelay // should not happen
    }
    max.fold(delay0)(_.min(delay0))
  }
}

object HostThrottle {

  /** What a host's pause means for a request we are about to send */
  sealed abstract class HoldOff

  /** Nothing to wait for */
  case object Clear extends HoldOff

  /** The host asked us to come back in `delay` */
  final case class Wait(delay: FiniteDuration) extends HoldOff

  /** The host asked to be left alone for `delay`, which is longer than we are willing to wait
    *
    * Waiting it out is not an option and coming back sooner than it asked is what gets the window
    * extended, so the only thing left is to give up - see `COURSIER_MAX_HTTP_RETRY_AFTER`.
    */
  final case class TooLong(delay: FiniteDuration) extends HoldOff

  private final class State(initialDelay: FiniteDuration) {

    /** Epoch millis before which no request should be sent to the host */
    @volatile var notBefore: Long = 0L

    /** The pause to use next if the host turns us away without saying for how long */
    @volatile var delay: FiniteDuration = initialDelay

    /** Whether `notBefore` comes from a `Retry-After` we decided not to honour */
    @volatile var tooLong: Boolean = false
  }

  /** Scheme, host and port - what a rate limit is actually applied to
    *
    * `None` for the URLs that have no host to speak of (`file:`, and the local paths that reach
    * here through custom protocols): there is nothing to rate limit there.
    */
  private def hostKey(url: String): Option[String] =
    for {
      uri    <- Try(new URI(url)).toOption
      scheme <- Option(uri.getScheme)
      host   <- Option(uri.getHost)
    } yield s"$scheme://$host:${uri.getPort}"
}
