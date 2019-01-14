---
title: CLI
---

coursier relies on [sbt-pack](https://github.com/xerial/sbt-pack) to build
its CLI from sources. [sbt-pack](https://github.com/xerial/sbt-pack) conveniently allows to build a launcher for
the CLI, either once, or continuously, while watching sources. It has the
advantage of not incurring the cost of merging multiple JARs together,
like [generating an assembly](https://github.com/sbt/sbt-assembly) would.

Note that this only applies during development, the
final `coursier` launcher generated upon each release relies on the
`coursier bootstrap` command, itself pulling a proguarded assembly of the
cli module of coursier.

## Batch mode

From freshly cloned sources, run
```bash
$ sbt cli/pack
```
to build a CLI launcher from sources.
This builds a
`modules/cli/target/pack` directory via
[sbt-pack](https://github.com/xerial/sbt-pack).
This directory contains a coursier launcher, that can be used like
```bash
$ modules/cli/target/pack/bin/coursier launch io.get-coursier:echo:1.0.1 -- foo
```

## Continuous mode

In a terminal window, run
```bash
sbt "~cli/pack"
```

This builds a `modules/cli/target/pack` directory like above, then
watches the sources of coursier for changes, and re-builds that directory
upon changes.

You can then run the coursier CLI from another terminal, like
```bash
$ modules/cli/target/pack/bin/coursier launch io.get-coursier:echo:1.0.1 -- foo
```

