package coursier.cache

import coursier.cache.HostThrottle.{Clear, TooLong, Wait}
import utest._

import java.time.{Clock, Instant, ZoneId, ZoneOffset}

import scala.concurrent.duration._

object HostThrottleTests extends TestSuite {

  /** A clock that only moves when told to, so that pauses can be checked to the millisecond */
  private final class ManualClock(private var now: Long) extends Clock {
    def getZone: ZoneId                        = ZoneOffset.UTC
    override def withZone(zone: ZoneId): Clock = this
    def instant(): Instant                     = Instant.ofEpochMilli(now)
    override def millis(): Long                = now
    def advance(by: FiniteDuration): Unit =
      now += by.toMillis
  }

  private def throttle(clock: Clock) =
    HostThrottle(
      initialDelay = 1.second,
      maxDelay = Some(1.minute),
      delayMultiplier = 2.0,
      maxRetryAfter = Some(5.minutes),
      clock = clock
    )

  private def url           = "https://fake.host/test/file.txt"
  private def other(i: Int) = s"https://fake.host/test/other$i.txt"

  val tests = Tests {

    test("a burst of 429s is one rate limit, not one per download") {
      val clock = new ManualClock(0L)
      val t     = throttle(clock)

      // six parallel downloads all turned away at once, none of them told for how long
      assert(t.rateLimited(url, None) == Some(1.second))
      for (i <- 1 to 5)
        assert(t.rateLimited(other(i), None).isEmpty)

      // the pause is the first step, not the sixth - and it is the one that was reported
      assert(t.holdOff(url) == Wait(1.second))
      clock.advance(1.second)
      assert(t.holdOff(url) == Clear)

      // and the next one is the second step
      assert(t.rateLimited(url, None) == Some(2.seconds))
      assert(t.holdOff(url) == Wait(2.seconds))
    }

    test("our own pause grows once per pause") {
      val clock = new ManualClock(0L)
      val t     = throttle(clock)

      val pauses =
        for (_ <- 1 to 8) yield {
          val pause = t.rateLimited(url, None)
          clock.advance(pause.get)
          pause.get
        }
      val expected = Seq(
        1.second,
        2.seconds,
        4.seconds,
        8.seconds,
        16.seconds,
        32.seconds,
        // capped from there on
        1.minute,
        1.minute
      )
      assert(pauses == expected)
    }

    test("a 429 that joins a pause still extends it to its own Retry-After") {
      val clock = new ManualClock(0L)
      val t     = throttle(clock)

      assert(t.rateLimited(url, None) == Some(1.second))
      assert(t.rateLimited(other(1), Some(30.seconds)).isEmpty)
      assert(t.holdOff(url) == Wait(30.seconds))

      // but not shortened by one that asks for less than what is already being waited for
      assert(t.rateLimited(other(2), Some(10.seconds)).isEmpty)
      assert(t.holdOff(url) == Wait(30.seconds))
    }

    test("a Retry-After too long to honour is not forgotten by a 429 that joins it") {
      val clock = new ManualClock(0L)
      val t     = throttle(clock)

      assert(t.rateLimited(url, Some(1.hour)) == Some(1.hour))
      assert(t.rateLimited(other(1), None).isEmpty)
      assert(t.holdOff(url) == TooLong(1.hour))
    }

    test("a Retry-After too long to honour is not honoured either when it joins a pause") {
      val clock = new ManualClock(0L)
      val t     = throttle(clock)

      assert(t.rateLimited(url, None) == Some(1.second))
      assert(t.rateLimited(other(1), Some(1.hour)).isEmpty)
      assert(t.holdOff(url) == TooLong(1.hour))
    }

    test("a normal answer starts the pause small again") {
      val clock = new ManualClock(0L)
      val t     = throttle(clock)

      assert(t.rateLimited(url, None) == Some(1.second))
      clock.advance(1.second)
      assert(t.rateLimited(url, None) == Some(2.seconds))
      clock.advance(2.seconds)

      t.succeeded(url)
      assert(t.rateLimited(url, None) == Some(1.second))
    }

    test("hosts are held off independently") {
      val clock = new ManualClock(0L)
      val t     = throttle(clock)

      assert(t.rateLimited(url, None) == Some(1.second))
      assert(t.holdOff("https://other.host/test/file.txt") == Clear)
      // the port is part of what a rate limit applies to
      assert(t.holdOff("https://fake.host:8443/test/file.txt") == Clear)
    }

    test("local files are never held off") {
      val clock = new ManualClock(0L)
      val t     = throttle(clock)

      assert(t.rateLimited("file:///tmp/test/file.txt", None).isEmpty)
      assert(t.holdOff("file:///tmp/test/file.txt") == Clear)
    }

    def unreachableThrottle(clock: Clock) =
      HostThrottle(
        initialDelay = 1.second,
        maxDelay = Some(1.minute),
        delayMultiplier = 2.0,
        maxRetryAfter = Some(5.minutes),
        clock = clock,
        unreachableAfter = Some(3),
        unreachableFor = 1.minute
      )

    /** A download that started, then failed to connect to `url0`'s host */
    def failedDownload(t: HostThrottle, url0: String): Unit =
      t.connectionFailed(url0, t.connectionMark(url0))

    test("a host becomes unreachable after downloads failing to connect in a row") {
      val clock = new ManualClock(0L)
      val t     = unreachableThrottle(clock)

      failedDownload(t, url)
      failedDownload(t, other(1))
      assert(t.unreachable(url).isEmpty)

      failedDownload(t, other(2))
      assert(t.unreachable(url) == Some(1.minute))
      assert(t.unreachable("https://other.host/test/file.txt").isEmpty)
      // it is not a rate limit
      assert(t.holdOff(url) == Clear)

      clock.advance(1.minute)
      assert(t.unreachable(url).isEmpty)
      // still no answer since: the next failure is enough
      failedDownload(t, url)
      assert(t.unreachable(url) == Some(1.minute))

      // an answer starts the count over
      t.succeeded(url)
      assert(t.unreachable(url).isEmpty)
      failedDownload(t, url)
      failedDownload(t, url)
      assert(t.unreachable(url).isEmpty)
    }

    test("downloads failing together are one failure") {
      val clock = new ManualClock(0L)
      val t     = unreachableThrottle(clock)

      // six parallel downloads, all started before any of them failed
      val marks = (1 to 6).map(n => t.connectionMark(other(n)))
      for ((mark, n) <- marks.zipWithIndex)
        t.connectionFailed(other(n + 1), mark)
      assert(t.unreachable(url).isEmpty)

      failedDownload(t, url)
      failedDownload(t, url)
      assert(t.unreachable(url) == Some(1.minute))
    }

    test("a download failing late doesn't extend the pause") {
      val clock = new ManualClock(0L)
      val t     = unreachableThrottle(clock)

      // started before the host was found unreachable, waiting on its connect timeout
      val lateMark = t.connectionMark(url)
      failedDownload(t, url)
      failedDownload(t, url)
      failedDownload(t, url)
      assert(t.unreachable(url) == Some(1.minute))

      clock.advance(30.seconds)
      t.connectionFailed(url, lateMark)
      assert(t.unreachable(url) == Some(30.seconds))
    }

    test("a download failing after an answer it started before doesn't count") {
      val clock = new ManualClock(0L)
      val t     = unreachableThrottle(clock)

      val mark = t.connectionMark(url)
      t.succeeded(url)
      failedDownload(t, url)
      failedDownload(t, url)
      t.connectionFailed(url, mark)
      assert(t.unreachable(url).isEmpty)
      failedDownload(t, url)
      assert(t.unreachable(url) == Some(1.minute))
    }

    test("a 429 is an answer too") {
      val clock = new ManualClock(0L)
      val t     = unreachableThrottle(clock)

      failedDownload(t, url)
      failedDownload(t, url)
      t.rateLimited(url, Some(1.second))
      clock.advance(1.second)
      failedDownload(t, url)
      assert(t.unreachable(url).isEmpty)

      // and one that comes back while the host is thought unreachable ends that
      failedDownload(t, url)
      failedDownload(t, url)
      assert(t.unreachable(url) == Some(1.minute))
      t.rateLimited(url, Some(2.seconds))
      assert(t.unreachable(url).isEmpty)
      assert(t.holdOff(url) == Wait(2.seconds))
    }

    test("the nop throttle holds nothing off") {
      val t = HostThrottle.Nop

      assert(t.rateLimited(url, Some(30.seconds)).isEmpty)
      assert(t.holdOff(url) == Clear)
      t.succeeded(url)
      assert(t.holdOff(url) == Clear)
    }
  }
}
