# Cache

From the CLI, one can interact directly with the cache with `cs get`. One can also
adjust some cache parameters, that should apply to all applications using coursier.

## `cs get`

The `cs get` command of the coursier CLI allows to download files via the coursier cache:
```text
$ cs get https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.jar
~/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.jar
```

For URLs whose content might change, pass `--changing`, to check for updates if the last
check is older than the [TTL](features-cache.md#ttl):
```text
$ cs get https://repo1.maven.org/maven2/org/scala-lang/scala-library/maven-metadata.xml --changing
~/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-library/maven-metadata.xml
```

## Environment variables

### Cache location

Adjust the cache location with `COURSIER_CACHE`:
```text
$ export COURSIER_CACHE="/some/other/location"
```

### TTL

Adjust the TTL with `COURSIER_TTL`:
```text
$ export COURSIER_TTL="1 hour"
```

The value of `COURSIER_TTL` is parsed with `scala.concurrent.duration.Duration`.

### HTTP debugging

Set `COURSIER_HTTP_DEBUG=1` (or the `coursier.http.debug` Java property) to print
every HTTP request coursier makes on stderr, with the status the server answered,
the `WWW-Authenticate` challenge if any, and which credentials were attached:

```text
$ COURSIER_HTTP_DEBUG=1 cs resolve org.typelevel:cats-core_3:2.9.0
[coursier http] GET https://repo1.maven.org/maven2/org/typelevel/cats-core_3/2.9.0/cats-core_3-2.9.0.pom (no credentials)
[coursier http] HTTP 200 for https://repo1.maven.org/maven2/org/typelevel/cats-core_3/2.9.0/cats-core_3-2.9.0.pom
```

Passwords are never printed. Files already in the cache are not requested, so
they do not appear in this output.
