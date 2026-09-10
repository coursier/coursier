---
title: Retries and rate limiting
---

Coursier fetches metadata and artifacts over HTTP. Repositories occasionally fail a request, and
public ones increasingly ask clients that send too much traffic to slow down, with an
[HTTP 429](https://central.sonatype.org/faq/429-error/) response. This page describes what coursier
requests, how it retries, what it does when it is rate limited, and how to change each of these.

It follows the guidance repositories give to build tools, in particular
[Maven Central's](https://central.sonatype.org/faq/429-error/): cache, back off rather than retry
harder, make it easy to route traffic through a repository manager, and say who is asking.

## What coursier requests

Everything coursier downloads goes to its [cache](cache.md), and is only requested again when the
cache says it can change. In practice, a resolution or a fetch sends:

- **one request per module** for its POM, to the first repository that has it - plus one for its
  Gradle module file when Gradle module support is enabled. A POM for a given version never changes,
  so it is never requested again once cached;
- **one request per version listing** (`maven-metadata.xml`) for dependencies with a version range
  or a `latest.*` version. Listings can change, so they are requested again once their
  [TTL](ttl.md) has expired - 24 hours by default;
- **one request per artifact**, plus one for its SHA-1 checksum file when the server did not already
  send the checksum in an `X-Checksum-SHA1` header (Maven Central does). Artifacts of non-snapshot
  versions are never requested again once cached;
- **one last-modified check per changing artifact** (`-SNAPSHOT` versions and the like) once its TTL
  has expired. The artifact itself is downloaded again only when the server reports a newer copy.

Up to 6 downloads run in parallel by default. The `coursier.parallel-download-count` Java property,
or `--parallel` (`-n`) on the command line, change that. The command-line progress display prints
the URL of every file being downloaded.

## Failed requests

A request that fails - a connection error, a connect or read timeout, an SSL error, or an HTTP 5xx
response - is retried, with an exponential backoff between attempts. By default, coursier makes up
to 5 attempts, waiting 10 ms after the first failure and twice as long after each of the next ones,
up to 20 seconds.

| Java property | Default | Meaning |
|---|---|---|
| `coursier.exception-retry` | `5` | Number of attempts, including the first one. `1` means no retry. |
| `coursier.exception-retry-backoff-initial-delay` | `10 ms` | Delay before the second attempt |
| `coursier.exception-retry-backoff-multiplier` | `2.0` | How much the delay grows after each failed attempt |
| `coursier.exception-retry-backoff-max-delay` | `20 s` | Ceiling on that delay |

A `Retry-After` header on a 503 response is honoured, up to the `COURSIER_MAX_HTTP_RETRY_AFTER`
limit described below.

## Rate limiting

An HTTP 429 response is not a failure: the server works, and asked to be left alone for a while.
Coursier handles it separately from the failures above.

- **It never comes back sooner than asked.** When the response carries a `Retry-After` header,
  coursier waits at least that long. Without one, it waits 1 second the first time, twice as long
  each following time, up to 1 minute.
- **The pause applies to the whole host, not to one download.** The other downloads in flight
  towards that host, in that JVM, wait it out too, rather than each sending a request that would
  only be rejected - and count against the limit.
- **Downloads do not all come back at once.** A small random delay, of up to one second, is added
  on top of the pause, so that the downloads that were told to come back at the same time do not
  trip the limit again together.
- **Waiting does not consume the retry budget.** The attempts above are kept for actual failures.
  What bounds a rate-limited download is a budget of wall clock time instead:
  `COURSIER_MAX_THROTTLE_WAIT`.
- **It gives up rather than retrying harder.** A `Retry-After` longer than
  `COURSIER_MAX_HTTP_RETRY_AFTER` is not shortened and retried at that pace: the download fails
  right away, after a single request. A download that has waited for `COURSIER_MAX_THROTTLE_WAIT`
  in total fails too.

When a host asks coursier to slow down, the command line logs it once per pause:

```text
Rate limited by repo1.maven.org, holding off for 30s
```

and a download that ends up giving up reports the response code and what the server asked for:

```text
retryable HTTP error: https://repo1.maven.org/maven2/… (HTTP 429, Retry-After: 1 hour)
```

The defaults are higher when the `CI` environment variable is set - which the usual CI services
do - as that is where being turned away for a few minutes is both most likely and most worth
sitting out.

| Setting | Environment variable | Java property | Default | In CI |
|---|---|---|---|---|
| Longest `Retry-After` honoured | `COURSIER_MAX_HTTP_RETRY_AFTER` | `coursier.max-http-retry-after` | `5 s` | `1 min` |
| Total time one download may spend rate limited | `COURSIER_MAX_THROTTLE_WAIT` | `coursier.max-throttle-wait` | `1 min` | `5 min` |
| First pause when there is no `Retry-After` | | `coursier.retry-throttle-initial-delay` | `1 s` | |
| Ceiling on that pause | | `coursier.retry-throttle-max-delay` | `1 min` | |
| How much that pause grows each time | | `coursier.exception-retry-backoff-multiplier` | `2.0` | |

Being rate limited by a public repository is a sign that too much traffic reaches it from your
network as a whole, and that is what to look at: see [Reducing traffic](#reducing-traffic) below.
Raising these limits only makes coursier wait longer for a block that lifts on its own.

### Setting the values

Durations are parsed with `scala.concurrent.duration.Duration`, so `30s`, `2 min`, and `1 hour`
are all fine.

Java properties are passed to the `cs` command with `-J-D…`, before the command:

```text
cs -J-Dcoursier.max-throttle-wait="10 min" fetch org.scala-lang:scala-library:2.13.16
```

When coursier is used from sbt, the properties are prefixed with `lmcoursier.internal.shaded.`,
like `lmcoursier.internal.shaded.coursier.max-throttle-wait`. The environment variables are the
same everywhere.

## Identifying coursier to repositories

Every request carries a `User-Agent` header, `Coursier/2.0` by default. The `coursier.http.agent`
Java property replaces it. Repositories ask that tools identify themselves with a product name, a
version, and a way to reach whoever runs them, so a tool embedding coursier, or a large
installation, is better off sending its own:

```text
-Dcoursier.http.agent="my-tool/1.4.0 (contact: ops@example.com)"
```

That is also what a repository operator looks for when investigating where traffic comes from.

## Reducing traffic

Retries and pauses are only there for the occasional failure. What keeps a repository from rate
limiting you in the first place is sending it fewer requests:

- **Keep the cache between runs.** In CI, persist the [cache directory](cache.md) across jobs, and
  in container images, populate it when building the image (with `cs fetch`, for example) rather
  than at start-up. A fresh environment fetching the same dependency tree on every run is exactly
  the pattern public repositories block.
- **Do not fetch at runtime.** Launchers created with `cs bootstrap --standalone` embed their
  dependencies instead of downloading them on first run.
- **Go through a repository manager.** [Mirrors](other-mirrors.md) route the requests that would go
  to a public repository to an internal one, without changing any build. `COURSIER_REPOSITORIES`
  replaces the default repositories altogether.
- **Raise the TTL** of changing things, with [`COURSIER_TTL`](ttl.md), when snapshots and version
  listings are checked more often than needed.
- **Work offline** when everything is already cached: `--mode offline` on the command line, or
  `COURSIER_MODE=offline`.
- **Lower the parallelism**, with `--parallel` or `coursier.parallel-download-count`, when a
  repository limits the number of concurrent connections rather than the request rate.

## API

The retry settings are fields of `FileCache`, and the same defaults apply. Here they are all set
explicitly:

```scala
import coursier.cache.FileCache
import coursier.util.Task

import scala.concurrent.duration._

val cache = FileCache[Task]().copy(
  // failed requests
  retry = 5,
  retryBackoffInitialDelay = 10.millis,
  retryBackoffMultiplier = 2.0,
  retryBackoffMaxDelay = Some(20.seconds),
  // rate limiting: the total time one download may spend being told to come back later
  maxThrottleWait = Some(1.minute)
)
```

The per-host pause is kept by a `HostThrottle` shared by all the caches of a JVM, since a rate limit
applies to the process as a whole. `FileCache` accepts one of its own through its `hostThrottle`
field, which is mostly useful in tests. `HostThrottle` is a trait, so that field also takes
`HostThrottle.Nop`, which holds nothing off: rate limits are then left to the retry loop alone,
bounded by `maxThrottleWait` only.

A `CacheLogger` is told about rate limits through its `rateLimited(url, duration)` method, called
once per pause: it is what prints the message above on the command line, and what to override to
surface it elsewhere.
