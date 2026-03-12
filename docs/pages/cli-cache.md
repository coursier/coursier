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
