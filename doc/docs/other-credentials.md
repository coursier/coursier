---
title: Credentials
---

Credentials can be passed either:
- [inline](#inline), via the `COURSIER_CREDENTIALS` environment variable or the `coursier.credentials` Java property,
- via [a property file](#property-file),
- via [a netrc file](#netrc-file), or
- [per host](#per-host), the former / legacy way (which suffers some limitations).

## Inline

Pass credentials via the `COURSIER_CREDENTIALS` environment variable or the
`coursier.credentials` Java property.

Example
```bash
$ export COURSIER_CREDENTIALS="
    artifacts.foo.com(the realm) alex:my-pass
    oss.sonatype.org alex-sonatype:aLeXsOnAtYpE
"
$ cs fetch -r https://artifacts.foo.com/maven …
```

It should contain lines like
- `host user:password`
- `host(realm) user:password`

The first one matches any realm.
The second one only matches a specific HTTP Basic auth realm.

`host` can also specify a port, like `artifacts.foo.com:8443`, or a scheme, like
`https://artifacts.foo.com` or `http://nexus.local:8081` (see
[How credentials are matched](#how-credentials-are-matched)).

Inline credentials are only sent over HTTPS, unless `host` starts with `http://`
(see [Plain HTTP repositories](#plain-http-repositories)).

Space characters at the beginning of each line are ignored. Empty lines
are ignored too.

Usernames can contain spaces (but not colons). Passwords can contain any
character including colons and trailing spaces.

From the CLI, you can also pass those explicitly via the `--credentials` option,
like
```bash
$ cs fetch -r https://artifacts.foo.com/maven \
    --credentials "artifacts.foo.com(the realm) alex:my-pass" \
    --credentials "oss.sonatype.org alex-sonatype:aLeXsOnAtYpE" \
    …
```

Pass `--use-env-credentials=false` to also ignore the content of
`COURSIER_CREDENTIALS` (environment) and `coursier.credentials` (Java property).
Only `--credentials` and `--credential-file` are considered then.

## Property file

Credentials can be passed via property files too. By default,
`~/.config/coursier/credentials.properties` is read
(`~/Library/Application Support/Coursier/credentials.properties` on OS X).

Any `.properties` files found in `~/.config/coursier/credentials/`
(`~/Library/Application Support/Coursier/credentials/` on OS X) are also
read automatically, making it easy to keep each repository's credentials
in a separate file.

### Format

The credential property file format is like
```
simple.username=simple
simple.password=SiMpLe
simple.host=artifacts.simple.com
simple.realm=simple realm

otherone.username=j
otherone.password=imj
otherone.host=nexus.other.com
```

This file is loaded with [`java.util.Properties.load`](https://docs.oracle.com/javase/tutorial/essential/environment/properties.html).

The username, password, host, and optional realm properties all start with
a common prefix (`simple` and `otherone` above). That prefix can be arbitrary.
It's only used to specify several credentials in a single file.

If no realm property is specified, the corresponding credentials are passed
to the host, no matter its HTTP Basic auth realm.

The following optional properties can also be set per prefix:

| Property | Default | Description |
|---|---|---|
| `prefix.realm` | (unset) | Match only if the server's HTTP Basic auth realm equals this value |
| `prefix.https-only` | `true` (`false` up to coursier 2.1.26) | Only send credentials over HTTPS connections (ignored if `prefix.host` has a scheme, see [Plain HTTP repositories](#plain-http-repositories)) |
| `prefix.auto` | `true` | Automatically match based on the host name (used during redirections) |
| `prefix.pass-on-redirect` | `false` | Forward credentials when the server issues an HTTP redirect |

Example with all optional properties:
```
myrepo.username=myuser
myrepo.password=mypass
myrepo.host=repo.example.com
myrepo.realm=My Repository
myrepo.https-only=true
myrepo.auto=true
myrepo.pass-on-redirect=false
```

To read a file from an alternate location, pass it via the
`COURSIER_CREDENTIALS` environment variable or `coursier.credentials`
Java property (same ones as above). Set its value to the full
path of your credential file, like `/home/alex/.credentials`.
`COURSIER_CREDENTIALS` and `coursier.credentials` are assumed to point to
a file if their value starts with `/`, or `file:` (URL format).

The CLI can also be passed a path via the `--credential-file` option (can be
specified multiple times).

Pass `--use-env-credentials=false` to also ignore the content of
`COURSIER_CREDENTIALS` (environment) and `coursier.credentials` (Java property).
Only `--credentials` and `--credential-file` are considered then.

## netrc file

The CLI reads credentials from a [netrc file](https://everything.curl.dev/usingcurl/netrc.html)
too, like `curl --netrc-optional` does. That file is the one the `COURSIER_NETRC`
environment variable points at, or else the one `NETRC` points at, or else
`~/.netrc` (on Windows, `~/_netrc` too if `~/.netrc` doesn't exist).

```text
machine artifacts.foo.com
  login alex
  password my-pass

machine nexus.local login alex password "my other pass"

default login anonymous password anon-pass
```

coursier parses it the way curl does: `machine`, `login`, `password`, and `default`
entries, with values that can be quoted (`"…"`, with `\"`, `\n`, `\r`, and `\t`
escapes), `account` values ignored, `macdef` macros skipped up to the next empty line,
and lines starting with `#` ignored. Entries lacking a login or a password are ignored.

These credentials are applied like curl applies them, which differs from the other
credentials coursier reads:
- a `machine` entry only looks at the host: its credentials are sent to that host
  whatever the scheme (plain HTTP included) and the port,
- the `default` entry matches any host,
- the first entry that matches wins - when a repository URL has a user but no
  password, like `-r https://alex@artifacts.foo.com/maven`, the first entry with that
  login wins,
- the other credentials coursier is passed (inline, property files, Scala CLI
  config, repository URLs) win over the netrc file.

Like the other credentials read from files or the environment, they're only sent once
the server answers `401` (see [Repositories that don't ask for credentials](#repositories-that-dont-ask-for-credentials)),
and aren't passed on redirections: the credentials of the redirection target are
looked up again in the netrc file.

The CLI accepts the same options as curl:

| Option | Description |
|---|---|
| `--netrc` | Read the netrc file, and fail if it doesn't exist or has a syntax error |
| `--netrc-optional` | Read the netrc file if it exists, ignore it or what follows a syntax error otherwise (the default) |
| `--netrc-file <path>` | Read that file rather than the default one. Fails if it doesn't exist or has a syntax error, unless `--netrc-optional` is passed too |
| `--netrc=false` | Don't read any netrc file |

Unlike curl, coursier reads the netrc file by default.

Bootstrap launchers generated by `cs bootstrap` (by coursier versions newer than 2.1.26)
read the default netrc file too (`COURSIER_NETRC`, `NETRC`, `~/.netrc`) when downloading
their JARs, after the other credentials, and ignore it if it's missing or malformed. Like
for their other credentials, they send these right away, without waiting for the server to
ask for them - so beware of `default` entries there, which are sent to any host the launcher
downloads JARs from.

From the API, netrc files are only read if a `NetrcCredentials` is passed to the cache
(see [API](#api)), or, with the Java API, if `coursierapi.Cache#withNetrc(true)` is called:

```java
Cache cache = Cache.create()
    .withNetrc(true)
    .withNetrcFile("/home/alex/.netrc-work") // optional, the default file otherwise
    .withNetrcOptional(false);               // optional, like curl --netrc
```

## Scala CLI config file

If a [Scala CLI](https://scala-cli.virtuslab.org/) configuration file is present
(at `~/.config/scala-cli/config.json`, or as specified by the `SCALA_CLI_CONFIG`
environment variable / `scala-cli.config` Java property), coursier reads repository
credentials from it as well.

Credentials are stored under the `repositories.credentials` key, for example:
```json
{
  "repositories": {
    "credentials": [
      {
        "host": "repo.example.com",
        "user": "value:myuser",
        "password": "value:mypass",
        "realm": "My Repository",
        "httpsOnly": false,
        "matchHost": true
      }
    ]
  }
}
```

## How credentials are matched

When coursier makes an HTTP request that requires authentication, it looks for
a matching credential entry using the following rules:

- The credential's `host` must match the URL's host.
- If `host` is a bare host name, like `repo.example.com`, the URL can use any port.
  It must use HTTPS, unless `https-only` is `false`, in which case plain HTTP is
  accepted too.
- If `host` has a port, like `repo.example.com:8443`, the URL must use that port.
- If `host` has a scheme, like `https://repo.example.com` or
  `http://nexus.local:8081`, the URL must use that scheme, and that port (or the
  scheme's default port, 443 for HTTPS and 80 for HTTP, if `host` has none).
  `https-only` is ignored then, so that's the way to send credentials to a single
  plain HTTP repository.
- If a `realm` is specified, it must match the realm advertised in the server's
  `WWW-Authenticate` response header.
- If `auto` is `true` (the default), the credential is also eligible to
  authenticate requests that result from HTTP redirections to the same host.
- If `pass-on-redirect` is `false` (the default), credentials are **not**
  forwarded when the server redirects to a different host. Set it to `true`
  only if you explicitly need cross-host credential forwarding.

The first matching credential is used. No credential is used if none matches.

## Plain HTTP repositories

In coursier versions newer than 2.1.26, credentials are only sent over HTTPS by
default. Up to 2.1.26, `https-only` defaulted to `false`, and credentials that
didn't set it were sent over plain HTTP too. Since credentials were matched on
the host name only, anyone able to tamper with a plain HTTP request, by
redirecting it to `http://` and the host of your repository, could then
get them in clear, even if you only ever accessed that repository over HTTPS.

That changes things for setups that rely on the former default to send
credentials to a repository served over plain HTTP. Such repositories now answer
`401 Unauthorized`, as credentials aren't sent to them anymore. With
`COURSIER_HTTP_DEBUG=1` (see [Debugging](#debugging)), the credentials that
were skipped for that reason are marked `(HTTPS only)`:

```text
[coursier http] http://nexus.local:8081/…/lib-1.0.0.pom: no credentials match host nexus.local (realm "Sonatype Nexus Repository Manager"), credentials are configured for: nexus.local (HTTPS only), giving up
```

To keep sending credentials to such a repository, either:
- put its scheme and port in `host`, like `http://nexus.local:8081`, so that the
  credentials only go to that repository over plain HTTP, or
- set `https-only` to `false`, so that they go to any port of that host over plain
  HTTP, like before.

The first option is the safer one, but coursier 2.1.26 and older don't understand
a scheme or a port in `host`. They never send such credentials from a property
file or the Scala CLI config file. Inline, they reject the `--credentials` value,
and ignore *all* the credentials in `COURSIER_CREDENTIALS` / `coursier.credentials`
if any line has one. If the same credentials have to work with these versions too,
use the second option.

Depending on where the credentials come from:

| Source | Scheme in `host` | Allowing plain HTTP for a bare host |
|---|---|---|
| [Inline](#inline) | `http://nexus.local:8081 user:password` | not possible, put the scheme in `host` |
| [Property file](#property-file) | `prefix.host=http://nexus.local:8081` | `prefix.https-only=false` |
| [Scala CLI config file](#scala-cli-config-file) | `"host": "http://nexus.local:8081"` | `"httpsOnly": false` |
| [API](#api), `DirectCredentials` | `DirectCredentials("http://nexus.local:8081", user, password)` | `.copy(httpsOnly = false)` |
| `coursierapi.Credentials` (interface versions newer than 1.0.30) | `Credentials.of("http://nexus.local:8081", user, password)` | `.withHttpsOnly(false)` |

This also applies to bootstrap launchers (generated by `cs bootstrap`) that
download their JARs at startup: those generated by coursier versions newer than
2.1.26 only send credentials over HTTPS by default too. Launchers generated
by older versions keep their former behavior.

Credentials embedded in a repository URL, like `-r http://user:password@nexus.local:8081/…`
(see [Per host](#per-host)), aren't concerned: they're sent to that
repository whatever its scheme.

## Repositories that don't ask for credentials

Credentials read from the environment or from files are only sent once the server
has answered `401` with a `WWW-Authenticate` header. A few repositories never send
that challenge: Azure DevOps artifact feeds answer an anonymous request with
`203 Non-Authoritative Information` and a sign-in page as the body. Coursier has
nothing to react to there, and the sign-in page ends up in the cache under the
artifact's name, where it looks like a successful download until something tries
to read the jar.

Pass `--reject-non-authoritative-responses`, or its shorter alias `--fail-203`,
to fail on such a response instead:

```text
$ cs fetch --fail-203 -r https://pkgs.dev.azure.com/… …
Error downloading com.example:lib:1.0.0
  non-authoritative information: https://pkgs.dev.azure.com/…/lib-1.0.0.pom (HTTP 203)
```

That makes the problem visible, but it does not authenticate the request. To do
that, embed the credentials in the repository URL
(`-r https://user:password@pkgs.dev.azure.com/…`) - those are sent right away,
without waiting for a challenge.

From the API, `FileCache#withAuthRealm` covers the same case for credentials a
repository carries: it tells the cache which realm to assume, so that credentials
scoped to that realm go out on the first request rather than waiting for a
challenge that never comes.

## Debugging

To see what coursier does on the wire, set `COURSIER_HTTP_DEBUG=1` (or the
`coursier.http.debug` Java property). Every HTTP request is then printed on
stderr, along with the status the server answered, the `WWW-Authenticate`
challenge if any, and which credentials were attached and where they came from:

```text
$ COURSIER_HTTP_DEBUG=1 cs resolve org.typelevel:cats-core_3:2.9.0
[coursier http] GET https://artifacts.foo.com/maven/org/typelevel/cats-core_3/2.9.0/cats-core_3-2.9.0.pom (no credentials)
[coursier http] HTTP 401 for https://artifacts.foo.com/maven/org/typelevel/cats-core_3/2.9.0/cats-core_3-2.9.0.pom (WWW-Authenticate: Basic realm="Artifactory Realm")
[coursier http] retrying https://artifacts.foo.com/maven/org/typelevel/cats-core_3/2.9.0/cats-core_3-2.9.0.pom with credentials for user alex
[coursier http] GET https://artifacts.foo.com/maven/org/typelevel/cats-core_3/2.9.0/cats-core_3-2.9.0.pom (credentials for user alex)
[coursier http] HTTP 200 for https://artifacts.foo.com/maven/org/typelevel/cats-core_3/2.9.0/cats-core_3-2.9.0.pom
```

Passwords are never printed. When no credentials match a challenge, the output
says which host and realm the server asked for, and which hosts (and realms)
credentials are configured for, which is usually enough to spot a typo:

```text
[coursier http] https://artifacts.foo.com/maven/…/cats-core_3-2.9.0.pom: no credentials match host artifacts.foo.com (realm "Artifactory Realm"), credentials are configured for: artifacts.foo.com(Artifactory realm), giving up
```

Note that credentials coming from the environment or from files are only sent
once the server has asked for them, so the first request for a given file is
always anonymous. Credentials embedded in a repository URL
(`-r https://user:password@…`) are sent right away.

Files already in the cache are not requested at all, and do not show up in this
output. To force requests for everything, run with an empty cache
(`COURSIER_CACHE=$(mktemp -d) cs …`).

## API

### DirectCredentials

`DirectCredentials` holds credentials for a single host directly in memory:

```scala mdoc:silent
import coursier.credentials.DirectCredentials

val directCred = DirectCredentials("repo.example.com", "myuser", "mypass")
  .withRealm(Some("My Repository"))
  .withHttpsOnly(true)
```

### FileCredentials

`FileCredentials` lazily reads credentials from a `.properties` file on disk:

```scala mdoc:silent
import coursier.credentials.FileCredentials

val fileCred = FileCredentials("/home/alex/.config/coursier/credentials.properties")
```

Pass `optional = false` to throw an exception if the file is not found
(by default the file is silently ignored when absent).

### NetrcCredentials

`NetrcCredentials` lazily reads credentials from a [netrc file](#netrc-file), the
default one (`COURSIER_NETRC`, `NETRC`, `~/.netrc`), or the one it's passed:

```scala mdoc:silent
import coursier.credentials.NetrcCredentials

val defaultNetrc = NetrcCredentials()
val otherNetrc   = NetrcCredentials(Some("/home/alex/.netrc-work"), optional = false)
```

Like `FileCredentials`, it ignores a missing file by default. It also ignores what
follows a syntax error then, like `curl --netrc-optional`. Pass `optional = false` to
throw an exception in both cases, like `curl --netrc`. Pass it last to the cache, so that
the other credentials win over it, like the CLI does.

### Using credentials when resolving

All these credential types implement the `Credentials` base class and can be passed
to a `FileCache`:

```scala mdoc:silent
import coursier._
import coursier.cache._
import coursier.credentials._

val cache = FileCache()
  .addCredentials(
    DirectCredentials("repo.example.com", "myuser", "mypass"),
    FileCredentials("/home/alex/.config/coursier/credentials.properties"),
    NetrcCredentials()
  )

val fetch = Fetch()
  .withCache(cache)
```

## Per host

This is the legacy way of passing credentials. Prefer the methods above
if that's a possibility.

The CLI still accepts credentials passed along specific repositories, like
```bash
$ cs fetch -r https://user:password@artifacts.foo.com/maven …
```

The API accept those too, when creating a repository, for example,
```scala mdoc:silent
import coursier.MavenRepository
import coursier.core.Authentication

val repo = MavenRepository(
  "https://user:password@artifacts.foo.com/maven",
  authentication = Some(Authentication("user", "password"))
)
```

Note that from now on (coursier `> 1.1.0-M13-2`), these credentials
are _not_ passed upon redirection. If you want to pass some credentials
when a redirection happens, pass them either [inline](#inline) or
via a [property file](#property-file), for the host of the redirection.
