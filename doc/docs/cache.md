---
title: Cache
---

## Path format

The cache is supposed to cache static resources, that don't change often (snapshot artifacts being the exception - these then follow TTL).

The content of a URL like
```bash
https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.12.7/scala-library-2.12.7.jar
```
is kept under
```
CACHE_PATH/https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.12.7/scala-library-2.12.7.jar
```

## Location

### Default location

On a system where only recent versions of coursier were ever run (>= `1.0.0-RC12-1`, released on the 2017/10/31), the default cache location is platform-dependent:
- on Linux, `~/.cache/coursier/v1`. This also applies to Linux-based CI environments, and FreeBSD too.
- on OS X, `~/Library/Caches/Coursier/v1`.
- on Windows, `%LOCALAPPDATA%\Coursier\Cache\v1`, which, for user `Alex`, typically corresponds to `C:\Users\Alex\AppData\Local\Coursier\Cache\v1`.

### Manual override

These locations take precedence over all the others:
- from the CLI, the location passed to the `--cache` option,
- from sbt-coursier, the location passed to the `coursierCache` setting,
- from the API, the location passed via the `cache` argument to most methods of the `coursier.Cache` object.

If none of these locations is specified, `COURSIER_CACHE` in the environment is looked at if it is set.

Else the `coursier.cache` Java property is looked at, if it is set.

Else the default location above is used.

### Compatibility with former versions

The default location above was set up in coursier `1.0.0-RC12-1` (released on 2017/10/31). The default cache path of former versions was `~/.coursier/cache/v1`, both on Linux and OS X.

In order not to re-download all artifacts again, the first version of coursier run on a system, either former or newer, sets the location for newer versions.

That is, if a version before `1.0.0-RC12-1` was run first, all versions of coursier , before and after `1.0.0-RC12-1`, will use `~/.coursier/cache/v1` by default.

If `1.0.0-RC12-1` or a newer version was run first, newer versions will use the new default location. Former versions, if ever they are used, will still rely on `~/.coursier/cache/v1`. It is expected these should not be used much any more, so that `~/.coursier/cache/v1` can be removed when you're confident no former version is used any more.

In more detail, 3 cases can happen:
- if the new location exists, it is used, even if the former location exists too. (It is assumed the former location was created by a former version run exceptionally, and doesn't correspond to the main cache location.)
- the new location doesn't exist, but the former one does. In that case, the former location is assumed to be the main cache, and is used.
- the new location doesn't exist, neither the former one. In that case, the new location is created, and should be used subsequently (first case above).
