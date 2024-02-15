---
title: Repositories
---

In the CLI and the high level API, the default repositories are
- the Ivy2 local repository, `~/.ivy2/local`,
- [Maven Central](https://repo1.maven.org/maven2).

(Only Maven Central from Scala.JS.)

These can be changed via the `COURSIER_REPOSITORIES` environment variable,
which should contain a list of repositories, separated by `|`. Repositories
are parsed the same way as the `-r` option in the CLI, like
```
$ export COURSIER_REPOSITORIES="ivy2Local|central|sonatype:releases|jitpack|https://corporate.com/repo"
```

From the CLI, one can add extra repositories to a particular command with the
`-r` option, like
```bash
$ cs fetch -r jitpack sh.almond:scala-kernel_2.12.8:0.2.2
```

One can ignore the default repositories above or the ones from
`COURSIER_REPOSITORIES` by passing `--no-default`, like
```bash
$ cs fetch --no-default \
  -r central -r sonatype:snapshots \
  org.scalameta:scalafmt-cli_2.12:2.0.0-RC4+29-f2154330-SNAPSHOT
```

## Ivy Repositories

It is possible to use a custom Ivy repository and pattern by concatenating them:

```
$ cs fetch --repository  "https://corporate.com/ivy-repo/[org]/[module]/[baseRev](-[folderItegRev])/[module]-[baseRev](-[fileItegRev])(-[classifier]).[ext]" sh.almond:scala-kernel_2.12.8:0.2.2
```

## Repository resolution order

The resolution order of the repositories is controlled in coursier by two things - [modes](https://github.com/coursier/coursier/blob/595f7d7d48c0c38bded88531d1fe1cdeb3ccc07c/cli/src/main/scala-2.12/coursier/cli/options/CommonOptions.scala#L13) and [cache policies](https://github.com/coursier/coursier/blob/595f7d7d48c0c38bded88531d1fe1cdeb3ccc07c/cache/jvm/src/main/scala/coursier/CachePolicy.scala).

Modes are tried successively and for each mode, repositories themselves are tried successively.

By [default](https://github.com/coursier/coursier/blob/595f7d7d48c0c38bded88531d1fe1cdeb3ccc07c/cache/jvm/src/main/scala/coursier/CachePolicy.scala#L68), 
the first policy looks at what's in the cache, only trying to fetch / update the changing things (snapshots…) that are already in cache. 

The second policy only looks at what's already in cache (this one may be redundant with the first one…). 

The third one tries to actually fetch things.

If we have 3 repositories and a dependency is already in cache for the third one, it will be peaked in cache from the third repo (by the first two policies). 

Even if the dependency was actually available in the first or second repositories.

For example:

Let's say that we have configured coursier to use following repositories:
1. maven central
2. ivy2Local
3. nexus snapshots

If the dependency is already in the ivy2Local coursier will use it instead of reaching to maven central. 

That trades off some reproducibility (we may get different jars depending on the previous cache state) for less IO overhead (if something is in cache for any of the repositories, no network IO will happen).


