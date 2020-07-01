---
title: Credentials
---

Credentials can be passed either:
- [inline](#inline), via the `COURSIER_CREDENTIALS` environment variable or the `coursier.credentials` Java property,
- via [a property file](#property-file), or
- [per host](#per-host), the former / legacy way (which suffers some limitations).

## Inline

Pass credentials via the `COURSIER_CREDENTIALS` environment variable or the
`coursier.credentials` Java property.

Example
```bash
$ export COURSIER_CREDENTIALS="
    artifacts.foo.com(the realm) alex:my-pass
    oss.sonatype.org alex-sonatype:aLeXsOnAtYpE
"
$ cs fetch -r https://artifacts.foo.com/maven …
```

It should contain lines like
- `host user:password`
- `host(realm) user:password`

The first one matches any realm.
The second one only matches a specific HTTP Basic auth realm.

Space characters are the beginning of each line are ignored. Empty lines
are ignored too.

From the CLI, you can also pass those explicitly via the `--credentials` option,
like
```bash
$ cs fetch -r https://artifacts.foo.com/maven \
    --credentials "artifacts.foo.com(the realm) alex:my-pass" \
    --credentials "oss.sonatype.org alex-sonatype:aLeXsOnAtYpE" \
    …
```

Pass `--use-env-credentials=false` to also ignore the content of
`COURSIER_CREDENTIALS` (environment) and `coursier.credentials` (Java property).
Only `--credentials` and `--credential-file` are considered then.

## Property file

Credentials can be passed via property files too. By default,
`~/.config/coursier/credentials.properties` is read
(`~/Library/Application Support/Coursier/credentials.properties` on OS X).

### Format

The credential property file format is like
```
simple.username=simple
simple.password=SiMpLe
simple.host=artifacts.simple.com
simple.realm=simple realm

otherone.username=j
otherone.password=imj
otherone.host=nexus.other.com
```

This file is loaded with [`java.util.Properties.load`](https://docs.oracle.com/javase/tutorial/essential/environment/properties.html).

The username, password, host, and optional realm properties all start with
a common prefix (`simple` and `otherone` above). That prefix can be arbitrary.
It's only used to specify several credentials in a single file.

If no realm property is specified, the corresponding credentials are passed
to the host, no matter its HTTP Basic auth realm.

To read a file from an alternate location, pass it via the
`COURSIER_CREDENTIALS` environment variable or `coursier.credentials`
Java property (same ones as above). Set its value to the full
path of your credential file, like `/home/alex/.credentials`.
`COURSIER_CREDENTIALS` and `coursier.credentials` are assumed to point to
a file if their value starts with `/`, or `file:` (URL format).

The CLI can also be passed a path via the `--credential-file` option (can be
specified multiple times).

Pass `--use-env-credentials=false` to also ignore the content of
`COURSIER_CREDENTIALS` (environment) and `coursier.credentials` (Java property).
Only `--credentials` and `--credential-file` are considered then.

## Per host

This is the legacy way of passing credentials. Prefer the methods above
if that's a possibility.

The CLI still accepts credentials passed along specific repositories, like
```bash
$ cs fetch -r https://user:password@artifacts.foo.com/maven …
```

The API accept those too, when creating a repository, for example,
```scala mdoc:silent
import coursier.MavenRepository
import coursier.core.Authentication

val repo = MavenRepository(
  "https://user:password@artifacts.foo.com/maven",
  authentication = Some(Authentication("user", "password"))
)
```

Not that from now on (coursier `> 1.1.0-M13-2`), these credentials
are _not_ passed upon redirection. If you want to pass some credentials
when a redirection happens, pass them either [inline](#inline) or
via a [property file](#property-file), for the host of the redirection.
