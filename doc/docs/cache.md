---
title: Cache
---

This page is about the main, "standard", coursier cache. coursier has other kind of caches,
such its [archive cache](features-archive-cache.md), documented on its own page.

The coursier cache aims at being quite generic. "Cool URLs don't change", and the coursier
cache basically caches things using URLs as keys. It is used to cache artifacts that don't change
often, snapshot artifacts and the like being the exception - these follow a [TTL](#ttl).

## Path format

The content of a URL like
```text
https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.pom
```
is kept under
```text
CACHE_PATH/https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.pom
```

For example, on Linux, [this artifact](https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.pom)
ends up cached as
```text
~/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.pom
```

Using the coursier CLI, one can get this path with
```text
$ cs get https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.pom
~/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.pom
```

The exact location of the coursier cache on your system depends on your operating system (OS), see
[here](#cache-location).

## Standalone use

The coursier cache can be used independently of dependency resolution. It can be used
[via its API](api-cache.md) or [via the CLI](cli-cache.md).

## Cache location

### Default location

The exact location of the coursier cache is OS-dependent. It relies on the
[directories-jvm](https://github.com/dirs-dev/directories-jvm) library (more precisely,
[a slightly customized fork of it](https://github.com/coursier/directories-jvm)) to follow
OS conventions and put the coursier cache at the most appropriate location for your OS.

| OS | Location | Note |
|----|----------|------|
| Linux | `~/.cache/coursier/v1` | Follows the [XDG Base Directory specification](https://specifications.freedesktop.org/basedir-spec/latest/), so `XDG_CACHE_HOME` is taken into account. This also applies to Linux-based CI environments, and to FreeBSD. |
| macOS | `~/Library/Caches/Coursier/v1` |      |
| Windows | `%LOCALAPPDATA%\Coursier\Cache\v1` | Obtained via the Windows API. For user `Alex`, it typically corresponds to `C:\Users\Alex\AppData\Local\Coursier\Cache\v1`. |

These are the default locations on a system where only recent versions of coursier were ever run
(>= `1.0.0-RC12-1`, released on the 2017/10/31), see
[below](#compatibility-with-former-versions).

### Manual override

These locations take precedence over all the others:
- from the CLI, the location passed to the `--cache` option,
- from sbt-coursier, the location passed to the `coursierCache` setting,
- from the API, the location passed via the `location` field of `coursier.cache.FileCache`
  (see [the cache API](api-cache.md#cache-location)).

If none of these locations is specified, `COURSIER_CACHE` in the environment is looked at if it is set.

Else the `coursier.cache` Java property is looked at, if it is set.

Else the default location above is used.

### SBT

When using Coursier with SBT, you can check the location with `show csrCacheDirectory`.

To override it, use the `-Dsbt.coursier.home` system property or the `COURSIER_CACHE` environment variable.

### Compatibility with former versions

The default location above was set up in coursier `1.0.0-RC12-1` (released on 2017/10/31). The default cache path of former versions was `~/.coursier/cache/v1`, both on Linux and OS X.

In order not to re-download all artifacts again, the first version of coursier run on a system, either former or newer, sets the location for newer versions.

That is, if a version before `1.0.0-RC12-1` was run first, all versions of coursier , before and after `1.0.0-RC12-1`, will use `~/.coursier/cache/v1` by default.

If `1.0.0-RC12-1` or a newer version was run first, newer versions will use the new default location. Former versions, if ever they are used, will still rely on `~/.coursier/cache/v1`. It is expected these should not be used much any more, so that `~/.coursier/cache/v1` can be removed when you're confident no former version is used any more.

In more detail, 3 cases can happen:
- if the new location exists, it is used, even if the former location exists too. (It is assumed the former location was created by a former version run exceptionally, and doesn't correspond to the main cache location.)
- the new location doesn't exist, but the former one does. In that case, the former location is assumed to be the main cache, and is used.
- the new location doesn't exist, neither the former one. In that case, the new location is created, and should be used subsequently (first case above).

## TTL

The content pointed at by some URLs does change for some of them. That's the case
for things such as version listings on Maven Central like
[this one](https://repo1.maven.org/maven2/org/scala-lang/scala-library/maven-metadata.xml),
or so called "snapshot" artifacts like [these](https://s01.oss.sonatype.org/content/repositories/snapshots/io/get-coursier/versions_2.13/0.5.1-SNAPSHOT/).

The coursier cache doesn't guess itself if a file at a URL can change or not. This has to be
specified beforehand by cache user. For example, for dependency resolution, coursier marks
version listings or snapshot artifacts as changing, while it doesn't for other resources.

Changing files are checked for updates once their time-to-live (TTL) is elapsed. See
[the TTL page](ttl.md) for its default value, and how to change it.

The coursier cache keeps on disk the last time it checked changing files for updates:
```text
$ cs get https://repo1.maven.org/maven2/org/scala-lang/scala-library/maven-metadata.xml --changing
~/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-library/maven-metadata.xml
$ ls -a "$(dirname "$(cs get https://repo1.maven.org/maven2/org/scala-lang/scala-library/maven-metadata.xml --changing)")"
…
.maven-metadata.xml.checked
maven-metadata.xml
…
```

For a file named `thing`, the coursier cache writes an empty file named `.thing.checked`, whose
last modified time is the last time it checked for updates of this file.
