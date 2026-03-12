# Coursier Cache

This page is about the main, "standard", coursier cache. coursier has other kind of caches,
such its [archive cache](features-archive-cache.md), documented on its own page.

The coursier cache aims at being quite generic. "Cool URLs don't change", and the coursier
cache basically caches things using URLs as keys. For example,
on Linux, [this artifact](https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.pom)
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

The exact location of the coursier cache is OS-dependent. It relies on the
[directories-jvm](https://github.com/dirs-dev/directories-jvm) library (more precisely,
[a slightly customized fork of it](https://github.com/coursier/directories-jvm)) to follow
OS conventions and put the coursier cache at the most appropriate location for your OS.

| OS | Location | Note |
|----|----------|------|
| Linux | `~/.cache/coursier/v1/` | XDG… |
| macOS | `~/Library/Caches/Coursier/v1/` |      |
| Windows | `C:\Users\username\AppData\Local\Coursier\Cache\v1` | Windows API… |

## TTL

The content pointed at by some URLs does change for some of them. That's the case
for things such as version listings on Maven Central like
[this one](https://repo1.maven.org/maven2/org/scala-lang/scala-library/maven-metadata.xml),
or so called "snapshot" artifacts like [these](https://s01.oss.sonatype.org/content/repositories/snapshots/io/get-coursier/versions_2.13/0.5.1-SNAPSHOT/).

The coursier cache doesn't guess itself if a file at a URL can change or not. This has to be
specified beforehand by cache user. For example, for dependency resolution, coursier marks
version listings or snapshot artifacts as changing, while it doesn't for other resources.

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
