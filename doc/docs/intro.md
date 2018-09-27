---
title: Intro
hide_title: true
---

# coursier

*Pure Scala Artifact Fetching*

A Scala library to fetch dependencies from Maven / Ivy repositories

*This is the documentation for version @VERSION@*.


*coursier* is a dependency resolver / fetcher *à la* Maven / Ivy, entirely
rewritten from scratch in Scala. It aims at being fast and easy to embed
in other contexts. Its core embraces functional programming principles.

It handles many features of the Maven model, and is able to fetch metadata and
artifacts from both Maven and Ivy repositories. It handles parallel downloads
out-of-the-box without resorting to global locks.

It can be used [as an sbt plugin](quick-start-sbt.md), making it handle
most dependency resolutions in sbt.

Its command-line tool allows to
- easily list the transitive dependencies of applications or libraries,
- download and list their artifacts,
- run applications published via Maven / Ivy repositories,
- and many other things.

Lastly, it can be used as a library via its [API](quick-start-api.md) and has a Scala JS [demo](scala-js.md).



Released under the Apache license, v2.
