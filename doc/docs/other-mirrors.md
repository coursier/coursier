---
title: Repository mirrors
---

Repository mirrors replace repositories before coursier starts resolving dependencies. They are
useful when an organization proxies public repositories through an internal repository manager, or
when a geographically closer copy of a repository is available.

For example, this makes requests that would go to Maven Central use an internal repository instead:

```text
cs config repositories.mirrors \
  "https://nexus.example.com/repository/maven-public=https://repo1.maven.org/maven2"
```

The value has the form `destination=source1;source2;...`. Multiple mirror definitions can be passed
as separate arguments:

```text
cs config repositories.mirrors \
  "https://nexus.example.com/repository/maven-public=https://repo1.maven.org/maven2" \
  "https://plugins.example.com/maven=https://plugins.gradle.org/m2"
```

The `cs config` command writes these values to the shared Scala CLI configuration file. Coursier's
JVM API and CLI read mirrors from that file automatically. Set `SCALA_CLI_CONFIG`, or the
`scala-cli.config` Java property, to use another file.

## Mirror types

Coursier supports Maven mirrors and tree mirrors.

A **Maven mirror** replaces the root of a matching Maven repository. It is the default type in the
`repositories.mirrors` configuration:

```text
https://nexus.example.com/repository/maven-public=https://repo1.maven.org/maven2
```

Prefix the definition with `maven:` to make the type explicit. A source of `*` matches every Maven
repository, but does not match Ivy repositories:

```text
maven:https://nexus.example.com/repository/maven-public=*
```

A **tree mirror** replaces a URL prefix and keeps the rest of the repository path. It works for both
Maven and Ivy repositories. For example:

```text
tree:https://mirror.example.com/repositories=https://repo.example.com
```

With this definition, a Maven repository rooted at
`https://repo.example.com/releases` is changed to
`https://mirror.example.com/repositories/releases`.

Mirror definitions are checked in order. The first matching definition is used for each repository.
Authentication attached to the source repository is not copied to the mirror, so configure
[credentials](other-credentials.md) for the mirror host separately when it requires authentication.

## Legacy properties files

Mirrors can also be declared in a `mirror.properties` file:

```properties
internal.from=https://repo1.maven.org/maven2;https://plugins.gradle.org/m2
internal.to=https://nexus.example.com/repository/maven-public
internal.type=maven
```

Each mirror has an arbitrary name (`internal` above), a semicolon-separated `.from` value, a `.to`
value, and an optional `.type`. In properties files, `.type` defaults to `tree`; accepted values are
`maven` and `tree`.

By default, coursier looks for `mirror.properties` in its platform-specific configuration
directories. `COURSIER_CONFIG_DIR` (or the `coursier.config-dir` Java property) changes those
directories. `COURSIER_MIRRORS` (or `coursier.mirrors`) selects a properties file instead, while
`COURSIER_EXTRA_MIRRORS` (or `coursier.mirrors.extra`) adds another properties file.

## API

The high-level API loads configured mirrors by default. Mirrors can also be supplied explicitly:

```scala
import coursier.Resolve
import coursier.params.MavenMirror

val resolve = Resolve()
  .addMirrors(
    MavenMirror(
      Seq("https://repo1.maven.org/maven2"),
      "https://nexus.example.com/repository/maven-public"
    )
  )
```

Use `.noMirrors` when a resolution must ignore both the default mirrors and mirror configuration
files. `TreeMirror` provides the prefix-preserving behavior described above.
