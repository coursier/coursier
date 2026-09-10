# Repository mirrors

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
credentials for the mirror host separately when it requires authentication.

## Maven `settings.xml`

Coursier also honors the `mirrors` section of Maven's `settings.xml`, like

```xml
<settings>
  <mirrors>
    <mirror>
      <id>internal</id>
      <name>Internal mirror</name>
      <url>https://nexus.example.com/repository/maven-public</url>
      <mirrorOf>*</mirrorOf>
    </mirror>
  </mirrors>
</settings>
```

`mirrorOf` accepts the syntax Maven accepts: a comma-separated list of repository identifiers,
which can also contain `*` (any repository), `external:*` (any repository that is neither local
nor on `file:`), `external:http:*` (any non-local repository accessed over plain HTTP), and
negations like `!some-repo`.

Maven matches those identifiers against the `id` of the repositories it knows about. Coursier
repositories have no such identifier, so their root URL is matched instead, along with the
well-known Maven identifier of the repository if it has one - only `central`, for Maven Central.
That is, `<mirrorOf>central</mirrorOf>` mirrors Maven Central, and other repositories are named by
their root URL:

```xml
<mirrorOf>https://repo.example.com/releases</mirrorOf>
```

When a `server` element has the same `id` as a mirror, its `username` and `password` are passed to
that mirror. Values are read as they appear in the file: passwords encrypted with
`settings-security.xml` are not decrypted, and property references like `${env.NEXUS_USER}` are
not substituted.

Mirrors that are `blocked`, and mirrors whose `mirrorOfLayouts` leaves out the `default` layout,
are ignored: coursier has no way to block a repository, and doesn't support the other Maven
layouts.

By default, coursier reads `~/.m2/settings.xml` - the same file it reads proxy settings from.
`CS_MAVEN_HOME` (or the `cs.maven.home` Java property), then `MAVEN_HOME` (or `maven.home`),
add directories to look that file up in, ahead of `~/.m2`. The first of those directories that
actually holds a `settings.xml` file wins, so a `MAVEN_HOME` pointing at a Maven installation, as
it usually does, doesn't hide the settings file of the user.

`COURSIER_MAVEN_SETTINGS` (or `coursier.maven-settings`) points at a settings file directly,
skipping that lookup; set it to `false` to ignore Maven settings altogether.

Mirrors from the coursier configuration are checked before those from `settings.xml`.

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

Mirrors can be read from a Maven settings file explicitly too, with
`Resolve.mavenSettingsMirrors`, which accepts the path of a `settings.xml` file and returns
`MavenSettingsMirror` instances.
