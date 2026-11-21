package coursier.cache

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
  * Implementations are expected to be safe to share between threads, and meant to be: a throttle no
  * one else consults is only an elaborate way of writing `Thread.sleep`.
  */
trait HostThrottle {

  /** Records that `url`'s host asked us to slow down
    *
    * `retryAfterOpt` is what its `Retry-After` header said, if it sent one.
    *
    * Returns the pause when this 429 is what started it, and `None` when it only joined one already
    * under way - so that a burst of them, which is what a rate limit tends to produce, is worth
    * reporting once rather than once per thread that ran into it.
    */
  def rateLimited(url: String, retryAfterOpt: Option[FiniteDuration]): Option[FiniteDuration]

  /** Whether a request can be sent to `url`'s host, and if not, for how long it cannot */
  def holdOff(url: String): HostThrottle.HoldOff

  /** Records that `url`'s host answered us normally, so the next pause we pick starts small again
    */
  def succeeded(url: String): Unit

  /** What was recorded about `url`'s host so far, to be passed back to [[connectionFailed]]
    *
    * Taken before a download makes its first attempt, so that its failure can be told apart from
    * the ones of the downloads that ran alongside it.
    */
  def connectionMark(url: String): Long = 0L

  /** Records that a download could not connect to `url`'s host, all its attempts included
    *
    * Like a 429, this is about the host rather than the artifact: when a repository is down or out
    * of reach (a VPN that dropped, say), every request to it waits out its connect timeout, then
    * its retries, one artifact after the other.
    *
    * `mark` is what [[connectionMark]] returned before the download started.
    */
  def connectionFailed(url: String, mark: Long): Unit = ()

  /** How long requests to `url`'s host should still fail without being sent, if they should
    *
    * Kept apart from [[holdOff]], rather than being one more [[HostThrottle.HoldOff]], so that the
    * matches on the latter written before it existed stay exhaustive.
    */
  def unreachable(url: String): Option[FiniteDuration] = None
}

object HostThrottle {

  /** The throttle that actually holds requests off - see [[HostThrottle.Default]] */
  def apply(
    initialDelay: FiniteDuration = CacheDefaults.throttleInitialDelay,
    maxDelay: Option[FiniteDuration] = CacheDefaults.throttleMaxDelay,
    delayMultiplier: Double = CacheDefaults.retryBackoffMultiplier,
    maxRetryAfter: Option[FiniteDuration] = CacheDefaults.maxHttpRetryAfter,
    clock: Clock = Clock.systemDefaultZone()
  ): HostThrottle =
    new Default(initialDelay, maxDelay, delayMultiplier, maxRetryAfter, clock)

  /** The throttle that actually holds requests off - see [[HostThrottle.Default]]
    *
    * `unreachableAfter` is how many connection failures in a row make a host unreachable (`None`
    * never does), and `unreachableFor` how long requests to it then fail without being sent.
    */
  def apply(
    initialDelay: FiniteDuration,
    maxDelay: Option[FiniteDuration],
    delayMultiplier: Double,
    maxRetryAfter: Option[FiniteDuration],
    clock: Clock,
    unreachableAfter: Option[Int],
    unreachableFor: FiniteDuration
  ): HostThrottle =
    new Default(
      initialDelay,
      maxDelay,
      delayMultiplier,
      maxRetryAfter,
      clock,
      unreachableAfter,
      unreachableFor
    )

  /** Holds a host off for as long as it asked, or as long as our own backoff says
    *
    * Instances are safe to share between threads, and meant to be - see [[HostThrottle]].
    */
  final class Default(
    initialDelay: FiniteDuration,
    maxDelay: Option[FiniteDuration],
    delayMultiplier: Double,
    maxRetryAfter: Option[FiniteDuration],
    clock: Clock,
    unreachableAfter: Option[Int],
    unreachableFor: FiniteDuration
  ) extends HostThrottle {

    def this(
      initialDelay: FiniteDuration = CacheDefaults.throttleInitialDelay,
      maxDelay: Option[FiniteDuration] = CacheDefaults.throttleMaxDelay,
      delayMultiplier: Double = CacheDefaults.retryBackoffMultiplier,
      maxRetryAfter: Option[FiniteDuration] = CacheDefaults.maxHttpRetryAfter,
      clock: Clock = Clock.systemDefaultZone()
    ) =
      this(
        initialDelay,
        maxDelay,
        delayMultiplier,
        maxRetryAfter,
        clock,
        CacheDefaults.unreachableAfter,
        CacheDefaults.unreachableFor
      )

    private val states = new ConcurrentHashMap[String, State]

    private def state(url: String): Option[State] =
      hostKey(url).map(key => states.computeIfAbsent(key, _ => new State(initialDelay)))

    /** @inheritdoc
      *
      * The pause we observe is whichever is longer of `retryAfterOpt` and our own: waiting longer
      * than asked is always safe, where coming back sooner is what gets a rate limit window
      * extended. It also keeps a server that answers `Retry-After: 0` - or none at all - from being
      * retried as fast as the loop can go.
      *
      * A rate limit is expressed per second or per minute, which is why our own pause starts around
      * a second rather than at the millisecond scale that suits a connection error: retrying one 10
      * ms later is just another request for the limiter to reject, and several of them count
      * rejected requests toward the limit they are enforcing.
      *
      * Only the 429 that starts a pause grows our own pause, though. The ones that land while it is
      * under way come from requests that were already in flight when the first one came back - the
      * downloads running alongside it, which hit the same limit at the same moment. That is one
      * rate limit, not one per download: letting each of them grow the pause had six parallel
      * downloads jump from a second straight to half a minute, while reporting the second. What
      * those 429s do bring is a `Retry-After` of their own, which the pause is extended to when it
      * is longer.
      */
    def rateLimited(url: String, retryAfterOpt: Option[FiniteDuration]): Option[FiniteDuration] =
      state(url).flatMap { state0 =>
        state0.synchronized {
          // turning us away is still answering: whatever connections failed before, it is reachable
          state0.answered()
          val now = clock.millis()
          // Holding off for less than the server asked is what we refuse to do, so a `Retry-After`
          // we are not willing to sit out is one to give up on rather than shorten.
          val tooLong = retryAfterOpt.exists(retryAfter => maxRetryAfter.exists(retryAfter > _))
          if (state0.notBefore <= now) {
            val ours = state0.delay
            state0.delay = capped(ours * delayMultiplier, maxDelay)
            state0.tooLong = tooLong
            val delay = retryAfterOpt.fold(ours)(_.max(ours))
            state0.notBefore = now + delay.toMillis
            Some(delay)
          }
          else {
            state0.tooLong = state0.tooLong || tooLong
            for (retryAfter <- retryAfterOpt)
              state0.notBefore = math.max(state0.notBefore, now + retryAfter.toMillis)
            None
          }
        }
      }

    def holdOff(url: String): HoldOff =
      hostKey(url).flatMap(key => Option(states.get(key))) match {
        case None => Clear
        case Some(state0) =>
          val left = state0.notBefore - clock.millis()
          if (left <= 0L) Clear
          else if (state0.tooLong) TooLong(FiniteDuration(left, MILLISECONDS))
          else Wait(FiniteDuration(left, MILLISECONDS))
      }

    def succeeded(url: String): Unit =
      for {
        key    <- hostKey(url)
        state0 <- Option(states.get(key))
      } state0.synchronized {
        state0.delay = initialDelay
        state0.tooLong = false
        state0.answered()
      }

    override def connectionMark(url: String): Long =
      hostKey(url).flatMap(key => Option(states.get(key))).fold(0L)(_.generation)

    /** @inheritdoc
      *
      * A failure only counts when nothing was recorded about the host since the download started.
      * The downloads in flight when a connection to a host fails tend to fail together - a blip of
      * the network, a DNS lookup that failed for a moment - and that is one failure, not one per
      * download: counting each of them had six parallel downloads make a host unreachable for a
      * minute from a single incident. The same goes for a failure that lands after the host was
      * found unreachable, from a connection already under way then: it says nothing new, and isn't
      * allowed to push the end of the pause further.
      *
      * The failures are counted until the host answers again, so once it has been unreachable, the
      * first download that fails after the pause starts a new one.
      */
    override def connectionFailed(url: String, mark: Long): Unit =
      for {
        after  <- unreachableAfter
        state0 <- state(url)
      } state0.synchronized {
        if (state0.generation == mark) {
          state0.generation += 1L
          state0.connectFailures += 1
          val now = clock.millis()
          if (state0.connectFailures >= after && state0.unreachableUntil <= now)
            state0.unreachableUntil = now + unreachableFor.toMillis
        }
      }

    override def unreachable(url: String): Option[FiniteDuration] =
      for {
        key    <- hostKey(url)
        state0 <- Option(states.get(key))
        left = state0.unreachableUntil - clock.millis()
        if left > 0L
      } yield FiniteDuration(left, MILLISECONDS)

    private def capped(delay: Duration, max: Option[FiniteDuration]): FiniteDuration = {
      val delay0 = delay match {
        case f: FiniteDuration => f
        case _                 => initialDelay // should not happen
      }
      max.fold(delay0)(_.min(delay0))
    }
  }

  /** A throttle that holds nothing off, and remembers nothing
    *
    * Rate limits are then left to the retry loop alone, which sees every host as clear and comes
    * back as fast as its throttle budget allows - so a `FileCache` given this one wants a
    * `maxThrottleWait` of its own, that budget being all that then bounds the retries a 429 gets.
    */
  case object Nop extends HostThrottle {
    def rateLimited(url: String, retryAfterOpt: Option[FiniteDuration]): Option[FiniteDuration] =
      None
    def holdOff(url: String): HoldOff = Clear
    def succeeded(url: String): Unit  = ()
  }

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

    /** Connections that failed since the host last answered */
    @volatile var connectFailures: Int = 0

    /** Epoch millis before which requests to the host fail without being sent */
    @volatile var unreachableUntil: Long = 0L

    /** Bumped by every connection failure counted and every answer, see `Default.connectionFailed`
      */
    @volatile var generation: Long = 0L

    /** Records that the host answered, which says it can be connected to - to call with the lock */
    def answered(): Unit = {
      connectFailures = 0
      unreachableUntil = 0L
      generation += 1L
    }
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
