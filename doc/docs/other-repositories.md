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

### The local Maven repository

⚠️ _Avoid using `~/.m2/repository` as a repository if you can. This is maven's internal file-system cache and it's not meant to be read by external tools.
By using it you are risking running into some issues like e.g. directories with POMs inside but without corresponding jar files.
You can read more about it [here](https://github.com/ghostbuster91/demos/tree/coursier-not-found) and [here](https://github.com/coursier/coursier/issues/1832#issuecomment-1357764540)._ 

## Repository syntax

Coursier accepts repositories as strings in a number of places, most notably
on its command-line, in `COURSIER_REPOSITORIES`, and via its repository parser.

On the command-line, most commands accepting dependencies accept extra repositories via
`-r` or `--repository`:
```text
$ cs resolve -r google androidx.test.ext:junit:1.2.1
```

Via its API, you can use `coursier.parse.RepositoryParser` to get a
`coursier.core.Repository` out of a string:
```scala mdoc:silent
import coursier.parse.RepositoryParser
assert(RepositoryParser.repository("google") == Right(coursier.Repositories.google))
```

Several kind of values are accepted as repositories:

- a number of pre-defined values for the most commonly used repositories
- URLs, that are mapped to a Maven repository with the URL as root
- URLs prefixed with `ivy:`, optionally followed by "Ivy patterns", are mapped to Ivy repositories

### Short syntax

A number of predefined values are accepted:

| short syntax | long form | details |
|--------------|-----------|---------|
| `central`    | `https://repo1.maven.org/maven2` | Maven Central, the most commonly used Maven repository |
| `ivy2Local`  | `file://${ivy.home-${user.home}/.ivy2}/local/[defaultPattern]` | The local Ivy2 repository, where Mill, Scala CLI, or sbt, publish to when asked to publish locally |
| `m2Local`    | `file://${user.home}/.m2/repository` | The local Maven repository, whose use is NOT recommended from coursier, see below |
| `sonatype:$name` | `https://oss.sonatype.org/content/repositories/$name` | Sonatype, most commonly used service to push artifacts to Maven Central. Example: `sonatype:snapshots` |
| `sonatype-s01:$name` | `https://s01.oss.sonatype.org/content/repositories/$name` | New Sonatype servers. Example: `sonatype-s01:snapshots` |
| `scala-integration` or `scala-nightlies` | `https://scala-ci.typesafe.com/artifactory/scala-integration` | Repository where Scala 2 nighly artifacts are pushed |
| `jitpack` | `https://jitpack.io` | Service that automatically builds artifacts from a GitHub repository |
| `clojars` | `https://repo.clojars.org` | Repository commonly used in the Clojure ecosystem |
| `google` | `https://maven.google.com` | Google-managed repository, that distributes some Android artifacts in particular |
| `gcs` | `https://maven-central.storage-download.googleapis.com/maven2` | Google-managed mirror of Maven Central |
| `gcs-eu` | `https://maven-central-eu.storage-download.googleapis.com/maven2` | Google-managed mirror of Maven Central, should be Europe-based |
| `gcs-asia` | `https://maven-central-asia.storage-download.googleapis.com/maven2` | Google-managed mirror of Maven Central, should be Asia-based |

A number of less commonly-used short syntaxes are also accepted. See the parser sources
(currently living [here](https://github.com/coursier/coursier/blob/main/modules/coursier/shared/src/main/scala/coursier/internal/SharedRepositoryParser.scala)) for a more comprehensive list.

About `m2Local`, it is recommended not to use it from coursier. Maven uses it as a cache,
but sometimes doesn't download all artifacts of a given module version. As a consequence,
if coursier picks this repository during resolution for a module version, the artifacts that
have not been downloaded by Maven will be missing from coursier. There's currently no workaround
for this, apart from not using `m2Local` from coursier. Only use this repository if
you are aware of that caveat. See also [the warning above](#the-local-maven-repository).

### Maven repositories

Passing a URL is interpreted as a Maven repository with the URL as root, like:
```
https://oss.sonatype.org/content/repositories/snapshots
```

Such a repository is expected to have artifacts looking like
```text
https://oss.sonatype.org/content/repositories/snapshots/the/organization/the-name/X.Y.Z/the-name-X.Y.Z.pom
https://oss.sonatype.org/content/repositories/snapshots/the/organization/the-name/X.Y.Z/the-name-X.Y.Z.jar
```

(Note that this particular example also has a shorter syntax, `sonatype:snapshots`, see above.)

Note that the `file` scheme is accepted, so that local directories can be used as Maven repositories too:
```
file:///Users/alex/test-repo
```

In that case, artifacts such as those are expected:
```text
/Users/alex/test-repo/the/organization/the-name/X.Y.Z/the-name-X.Y.Z.pom
/Users/alex/test-repo/the/organization/the-name/X.Y.Z/the-name-X.Y.Z.jar
```

### Ivy repositories

These consist in a URL, prefixed with `ivy:`, and optionally followed by an "Ivy pattern":
```text
ivy:https://repo.typesafe.com/typesafe/ivy-releases/[organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]
```

Such a repository expects artifacts looking like
```text
https://repo.typesafe.com/typesafe/ivy-releases/the-organisation/the-name/X.Y.Z/ivys/ivy.xml
https://repo.typesafe.com/typesafe/ivy-releases/the-organisation/the-name/X.Y.Z/ivys/the-name.jar
```

`file` URLs are also accepted, and Java properties can also be used:
```text
file://${ivy.home-${user.home}/.ivy2}/local/[defaultPattern]
```

If the `ivy.home` Java property is set, its value is replaces the whole
of `${ivy.home-${user.home}/.ivy2}`. Else, the value after `-` is used.
If `user.home` is `/Users/alex`, we get `/Users/alex/.ivy2`. If a property
is unset, and has no fallback, parsing fails.

`[defaultPattern]` is a shorter syntax for a commonly used Ivy pattern. It's used by
the Ivy2 local repository, that Mill, Scala CLI, or sbt use.

(Note that that particular example has a shorter syntax: `ivy2Local`, see above.)

Such a custom Ivy repository and pattern can be passed on the command-line, like

```
$ cs fetch --repository "ivy:https://corporate.com/ivy-repo/[org]/[module]/[baseRev](-[folderItegRev])/[module]-[baseRev](-[fileItegRev])(-[classifier]).[ext]" sh.almond:scala-kernel_2.12.8:0.2.2
```

## Repository resolution order

The resolution order of the repositories is controlled in coursier by two things - [modes](https://github.com/coursier/coursier/blob/595f7d7d48c0c38bded88531d1fe1cdeb3ccc07c/cli/src/main/scala-2.12/coursier/cli/options/CommonOptions.scala#L13) and [cache policies](https://github.com/coursier/coursier/blob/595f7d7d48c0c38bded88531d1fe1cdeb3ccc07c/cache/jvm/src/main/scala/coursier/CachePolicy.scala).

Modes are tried successively and for each mode, repositories themselves are tried successively.

By [default](https://github.com/coursier/coursier/blob/595f7d7d48c0c38bded88531d1fe1cdeb3ccc07c/cache/jvm/src/main/scala/coursier/CachePolicy.scala#L68), 
the first policy looks at what's in the cache, only trying to fetch / update the changing things (snapshots…) that are already in cache. 

The second policy only looks at what's already in cache (this one may be redundant with the first one…). 

The third one tries to actually fetch things.

If we have 3 repositories and a dependency is already in cache for the third one, it will be picked in cache from the third repo (by the first two policies). 

Even if the dependency was actually available in the first or second repositories.

For example:

Let's say that we have configured coursier to use following repositories:
1. maven central
2. ivy2Local
3. nexus snapshots

If the dependency is already in the ivy2Local coursier will use it instead of reaching to maven central. 

That trades off some reproducibility (we may get different jars depending on the previous cache state) for less IO overhead (if something is in cache for any of the repositories, no network IO will happen).


