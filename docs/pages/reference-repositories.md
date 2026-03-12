Coursier accepts repositories as strings in a number of places, most notably
on its command-line and via its repository parser.

On the command-line, most commands accepting dependencies accept extra repositories via
`-r` or `--repository`:
```text
$ cs resolve -r google androidx.test.ext:junit:1.2.1
```

Via its API, you can use `coursier.parse.RepositoryParser` to get a
`coursier.core.Repository` out of a string:
```scala
import coursier.parser.RepositoryParser
RepositoryParser.repository("google") == Right(coursier.Repositories.google)
```

## Accepted values

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
you are aware of that caveat.

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
