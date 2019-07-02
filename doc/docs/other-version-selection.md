---
title: Version selection
---

This page aims at describing how versions are reconciled in coursier,
in particular when version intervals are involved.

First section details the general algorithm, the ones below illustrate
what happens in more specific examples.

## Principle

When we depend on several intervals and specific versions of a dependency,
we first take the intersection of the intervals. If it's empty, there's a conflict
(non overlapping intervals). If no intervals are passed, it's equivalent
to have interval `(,)` (matches all versions).

Then specific versions are compared to the resulting interval. Versions
below are ignored. Versions above the interval, if any, result in a conflict.
If there are versions in the interval, the highest takes over the interval.

This results in either an interval, or a specific version.

In the latter case, that resulting version is selected. In the case of a
resulting interval, the available versions are listed, and the highest
in the interval is selected.

## Latest wins

If we depend on several versions of a dependency,
the latest version is selected. It doesn't matter whether
that version was depended on directly, or only via transitive dependencies.

If we depend on version `1.0`, `1.1`, and `1.2`, version `1.2` gets selected.

For example, argonaut-shapeless `1.2.0-M11` depends on shapeless `2.3.3`.
If we depend on both argonaut-shapeless `1.2.0-M11` and shapeless `2.3.2`,
shapeless `2.3.3` gets selected:
```bash
$ coursier resolve \
    com.github.alexarchambault:argonaut-shapeless_6.2_2.12:1.2.0-M11 \
    com.chuusai:shapeless_2.12:2.3.2
com.chuusai:shapeless_2.12:2.3.3:default
com.github.alexarchambault:argonaut-shapeless_6.2_2.12:1.2.0-M11:default
io.argonaut:argonaut_2.12:6.2.3:default
org.scala-lang:scala-library:2.12.8:default
org.scala-lang:scala-reflect:2.12.8:default
org.typelevel:macro-compat_2.12:1.1.1:default
```

This goes the other way around too: argonaut-shapeless `1.2.0-M7` depends on
shapeless `2.3.2`. If we depend on both argonaut-shapeless `1.2.0-M7` and
shapeless `2.3.3`, shapeless `2.3.3` gets selected:
```bash
$ coursier resolve \
    com.github.alexarchambault:argonaut-shapeless_6.2_2.12:1.2.0-M7 \
    com.chuusai:shapeless_2.12:2.3.3
com.chuusai:shapeless_2.12:2.3.3:default
com.github.alexarchambault:argonaut-shapeless_6.2_2.12:1.2.0-M7:default
io.argonaut:argonaut_2.12:6.2:default
org.scala-lang:scala-library:2.12.4:default
org.scala-lang:scala-reflect:2.12.1:default
org.typelevel:macro-compat_2.12:1.1.1:default
```

## Latest version in interval

If we depend on a dependency only via an interval, highest version in the
interval gets selected.

If we depend on `[1.0,2.0)` and versions `1.0`, `1.1`, and `1.2`, are available,
then version `1.2` gets selected.

For example, as of writing this, `2.13.0` is the latest scala version. It gets selected
if we depend on scala-library `[2.12,)`:
```bash
$ coursier resolve 'org.scala-lang:scala-library:[2.12,)'
org.scala-lang:scala-library:2.13.0:default
```

`2.12+` corresponds to all `2.12.x` versions, starting from `2.12.0`. As of
writing this, this selects version `2.12.8`, the latest `2.12` scala version:
```bash
$ coursier resolve 'org.scala-lang:scala-library:2.12+'
org.scala-lang:scala-library:2.12.8:default
```

## Specific version over intervals

If we depend on both a version interval and a specific version, the specific
version gets selected.

If we depend on `[1.0,2.0)` and `1.2`, version `1.2` gets selected.

For example:
```bash
$ coursier resolve \
    'org.scala-lang:scala-library:2.12+' \
    org.scala-lang:scala-library:2.12.4
org.scala-lang:scala-library:2.12.4:default
```

That's provided the specific version lies in the interval.
If it's below, it's ignored. If it's above, we get a conflict.

If we depend on `[1.0,2.0)` and `2.1`, we get a conflict.

For example:
```bash
$ coursier resolve \
    'org.scala-lang:scala-library:2.12+' \
    org.scala-lang:scala-library:2.13.0
Resolution error: Conflicting dependencies:
org.scala-lang:scala-library:2.12+:default(compile)
org.scala-lang:scala-library:2.13.0:default(compile)
```

If the specific version lies below the interval, it is ignored.

If we depend on `[1.0,2.0)` and `0.9`, and `1.0` / `1.1` / `1.2` are available,
then `0.9` is ignored, and `1.2` is selected.

For example,
shapeless `2.3.2` depends on scala-library `2.12.0`. If we also depend
on scala-library `[2.12.1,2.12.8]`, the interval takes over, even though
`2.12.0` isn't in it:
```bash
$ coursier resolve \
    com.chuusai:shapeless_2.12:2.3.2 \
    'org.scala-lang:scala-library:[2.12.1,2.12.8]'
com.chuusai:shapeless_2.12:2.3.2:default
org.scala-lang:scala-library:2.12.8:default
org.typelevel:macro-compat_2.12:1.1.1:default
```

If we have both a version in the interval and one below it, the one below
is ignored, and the one in the interval gets selected:
```bash
$ coursier resolve \
    com.chuusai:shapeless_2.12:2.3.2 \
    'org.scala-lang:scala-library:[2.12.1,2.12.8]' \
    org.scala-lang:scala-library:2.12.7
com.chuusai:shapeless_2.12:2.3.2:default
org.scala-lang:scala-library:2.12.7:default
org.typelevel:macro-compat_2.12:1.1.1:default
```

## Several intervals

If we depend on several intervals for a dependency, their intersection is
used.
```bash
$ coursier resolve \
    'org.scala-lang:scala-library:[2.12.1,2.12.7]' \
    'org.scala-lang:scala-library:[2.12.2,2.12.8]'
org.scala-lang:scala-library:2.12.7:default
```
Here, `[2.12.1,2.12.7]` and `[2.12.2,2.12.8]` is equivalent to just
`[2.12.2,2.12.7]`, which gives version `2.12.7` at the end.

If the intersection of the intervals is empty, we get a conflict:
```bash
$ coursier resolve \
    'org.scala-lang:scala-library:[2.12.1,2.12.8]' \
    'org.scala-lang:scala-library:2.13+'
Resolution error: Conflicting dependencies:
org.scala-lang:scala-library:2.13+:default(compile)
org.scala-lang:scala-library:[2.12.1,2.12.8]:default(compile)
```

If several intervals and several specific versions are passed, the intervals
are replaced by their intersection. Then the rules above apply.

For example,
```bash
$ coursier resolve \
    'org.scala-lang:scala-library:[2.12.1,2.12.7]' \
    'org.scala-lang:scala-library:[2.12.2,2.12.8]' \
    org.scala-lang:scala-library:2.12.0
org.scala-lang:scala-library:2.12.7:default
```

```bash
$ coursier resolve \
    'org.scala-lang:scala-library:[2.12.1,2.12.7]' \
    'org.scala-lang:scala-library:[2.12.2,2.12.8]' \
    org.scala-lang:scala-library:2.12.0 \
    org.scala-lang:scala-library:2.12.6
org.scala-lang:scala-library:2.12.6:default
```
