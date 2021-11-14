---
title: Overview
hide_title: true
---

# coursier

Coursier is the Scala application and artifact manager.
It can install Scala applications and setup your Scala development environment.
It can also download and cache artifacts from the web.

## Getting started

[Install `cs` and your Scala development environment](cli-installation.md)

Once installed, the main usage of coursier is through its **command-line** tool `cs`.
Its features include:

- [install additional Scala applications](cli-install.md)
- [launch Scala applications](cli-launch.md)
- [create standalone launchers for Scala applications](cli-bootstrap.md)
- [and others](cli-overview.md)

## Under the hood

Under the hood, coursier is a dependency resolver / fetcher *à la* Maven / Ivy, entirely
rewritten from scratch in Scala. It aims at being fast and easy to embed
in other contexts. Its core embraces functional programming principles.

It handles many features of the Maven model, and is able to fetch metadata and
artifacts from both Maven and Ivy repositories. It handles parallel downloads
out-of-the-box without resorting to global locks.

The core features of coursier can be used [as a **library** via its API](api.md), on the JVM or [from Scala.js](api-scala-js.md).
See [Users](overview-in-the-wild.md) for an overview of third-party projects that do this.

*This is the documentation for version @VERSION@*.



Released under the Apache license, v2.
