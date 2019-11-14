---
title: sbt-coursier
---

`sbt-coursier` ensures one's dependencies are fetched via coursier rather
than by sbt itself, that relies on its own custom version of Ivy. When
properly set up (see below), coursier can also be used to fetch most sbt plugins. Note that sbt-coursier itself is still fetched by sbt. Also note that the JARs of sbt, when one launches sbt for the first time, are also still fetched by sbt itself.

It currently requires sbt 1.x. See
[coursier versions 1.0.x](https://github.com/coursier/coursier/tree/series/1.0.x)
for sbt 0.13.x.

The sbt plugin can be enabled either on a per-project basis (recommended), or globally.

It mainly overrides the `update`, `updateClassifiers`, and `updateSbtClassifiers` tasks. It does
not handle publishing for now.

> Note that if you are using sbt 1.3.x or later, you shouldn't need to add
sbt-coursier to your build, except in [very specific cases](#sbt-13x).
sbt 1.3.x ships with coursier support
out-of-the-box. That support is enabled by default.

## Setup

### Per project

#### No other plugin

Directly add sbt-coursier to `project/plugins.sbt`, with
```scala
@PLUGIN_EXTRA_SBT@addSbtPlugin("io.get-coursier" % "sbt-coursier" % "@PLUGIN_VERSION@")
```

#### Along other plugins

Add sbt-coursier in the meta-build, by adding the following to `project/project/plugins.sbt`,
```scala
@PLUGIN_EXTRA_SBT@addSbtPlugin("io.get-coursier" % "sbt-coursier" % "@PLUGIN_VERSION@")
```

This will use sbt-coursier to fetch sbt plugins from `project/*.sbt`. Then add sbt-coursier along the other
plugins by adding the following to `project/plugins.sbt`,
```scala
@PLUGIN_EXTRA_SBT@addSbtCoursier
```

### Globally

This method is not recommended. Adding plugins globally makes your builds less reproducible, as their
behavior now depends on the global plugins set up on the machine they are run. On the other hand, this
method is more straightforward than the above, as it applies to all projects at once.

Add the following to `~/.sbt/1.0/plugins/build.sbt`,
```scala
@PLUGIN_EXTRA_SBT@addSbtPlugin("io.get-coursier" % "sbt-coursier" % "@PLUGIN_VERSION@")
```

#### sbt Native Packager conflict

If you use `sbt-native-packager` and encounter a compile error `java.lang.NoClassDefFoundError: org/vafer/jdeb/Console`,
there is a work around described in: [#450](https://github.com/coursier/coursier/issues/450).

Add the following to `~/.sbt/1.0/global.sbt`,
```scala
// Work around for https://github.com/coursier/coursier/issues/450
classpathTypes += "maven-plugin"
```

#### sbt 1.3.x

sbt 1.3.x ships with coursier support out-of-the-box, and that support
is enabled by default. You shouldn't need to add sbt-coursier to sbt
1.3.x builds in most cases.

You may want to add it anyway:
- if your build or some of your plugins directly access coursier-specific
keys
- if you want your build to use a coursier release newer that the one sbt
depends on.

Note that in the first case, you may want to consider migrating to
the [sbt librarymanagement API](https://github.com/sbt/librarymanagement),
by using the `dependencyResolution` sbt key rather that accessing
coursier-specific keys.

In the second case, you'll have to use the
[coursier-based sbt launcher](https://github.com/coursier/sbt-launcher),
via [its custom sbt-extras launcher](https://github.com/coursier/sbt-extras)
for example. Then add sbt-coursier to your build, or its sister plugin,
sbt-lm-coursier, whose implementation is much closer to the coursier-support
in sbt itself.
