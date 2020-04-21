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
