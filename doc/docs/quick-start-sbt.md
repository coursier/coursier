---
title: sbt plugin
---

Enable the sbt plugin by adding
```scala
@PLUGIN_EXTRA_SBT@addSbtPlugin("io.get-coursier" % "sbt-coursier" % "@PLUGIN_VERSION@")
```
either
- to the `project/plugins.sbt` file of an sbt project, or
- to `~/.sbt/1.0/plugins/build.sbt` (globally, not recommended).

See [the dedicated page](sbt-coursier.md) for more details.

The sbt plugin only supports sbt 1.x. See the
[1.0.x versions](https://github.com/coursier/coursier/tree/series/1.0.x)
of coursier for sbt 0.13.x support.
