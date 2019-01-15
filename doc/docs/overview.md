---
title: Overview
hide_title: true
---

# coursier

*coursier* is a dependency resolver / fetcher *à la* Maven / Ivy, entirely
rewritten from scratch in Scala. It aims at being fast and easy to embed
in other contexts. Its core embraces functional programming principles.

It handles many features of the Maven model, and is able to fetch metadata and
artifacts from both Maven and Ivy repositories. It handles parallel downloads
out-of-the-box without resorting to global locks.

It can be used
- [as an **sbt plugin**](quick-start-sbt.md), making it handle most dependency resolutions in sbt,
- via its [**command-line** tool](quick-start-cli.md), that allows to
  - [easily list the transitive dependencies of applications or libraries](cli-resolve.md),
  - [download and list their artifacts](cli-fetch.md),
  - [run applications published via Maven / Ivy repositories](cli-launch.md),
  - [and many other things](cli-overview.md).
- [as a **library** via its API](quick-start-api.md), on the JVM or [from Scala.js](scala-js.md).

*This is the documentation for version @VERSION@*.



Released under the Apache license, v2.
