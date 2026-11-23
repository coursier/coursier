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

## Where to look?

The [coursier cache](cache.md) backs basically all coursier features. It should be important for you
to understand how it works.

What user are you?

### Mill user

You use coursier via [Mill](https://mill-build.org). Mill uses coursier to get and manage JVM dependencies.
It also uses it to fetch JVMs, if you're using the Mill native launcher, or Mill's
per module JVM capabilities.

* [archive cache](features-archive-cache.md)
* [JVM management](features-jvm.md)
* [Gradle Modules](features-gradle-modules.md)
* [BOM](features-bom.md)
* [dependency format](cli-dependency.md)

### Scala user

You use the coursier CLI to setup Scala on your machine, following the instructions
of the [scala-lang.org](https://www.scala-lang.org) website.

* [setup](cli-installation.md)
* [install](cli-install.md)
* [bootstraps](cli-bootstrap.md)
* [JVM](cli-java.md)

### Scala CLI user

You use coursier via [Scala CLI](https://scala-cli.virtuslab.org). Scala CLI relies on many coursier
features, most notably JVM dependencies resolution and JVM management.

* [dependency format](cli-dependency.md)
* [archive cache](features-archive-cache.md)
* [JVM management](features-jvm.md)

### Scala / sbt user

You use coursier via [sbt](https://www.scala-sbt.org). sbt uses coursier to get and manage JVM dependencies.

* [sbt-coursier](sbt-coursier.md)
* sbt particularities: relaxed [version reconciliation](other-version-handling.md), disabled
  [BOM](features-bom.md) support, no [Gradle Module](features-gradle-modules.md) support, …

### Bazel user

You use coursier via Bazel's [rules_jvm_external](https://github.com/bazel-contrib/rules_jvm_external).
rules_jvm_external uses the coursier CLI to fetch dependencies, and uses the coursier CLI's JSON report
output to read its results.

* [Gradle Modules](features-gradle-modules.md)
* [BOM](features-bom.md)
* [dependency format](cli-dependency.md)
* [JSON report](json-report.md)

### JVM user

You use the coursier CLI to manage JVMs on your machine or from scripts.

* [archive cache](features-archive-cache.md)
* [JVM management](features-jvm.md), and the [`java` and `java-home` commands](cli-java.md)

### Java user

You use the [coursier Java API](java-api.md) to download dependencies, and maybe JVMs or archives too.

* [Java API](java-api.md)
* [archive cache](features-archive-cache.md)
* [JVM management](features-jvm.md)

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
