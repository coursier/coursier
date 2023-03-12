---
title: Application descriptors
---

The [`install`](cli-install.md) command goes from an application name (`scala`, `ammonite`, `scalafmt`, etc.)
to actual dependencies via "channels", which contain one application descriptor
per application.

For example, the application descriptor of `mdoc` in the default channel contains:
```json
{
  "repositories": [
    "central"
  ],
  "dependencies": [
    "org.scalameta::mdoc:latest.stable"
  ]
}
```

`"repositories": ["central"]` says that mdoc should be pulled from
[Maven Central](https://repo1.maven.org/maven2), that `"central"` is an alias for.

`"dependencies": ["org.scalameta::mdoc:latest.stable"]` gives the Maven coordinates of
mdoc. Note the use of `::`, as mdoc is a Scala dependency, and of `latest.stable`
to automatically select the latest version, excluding versions corresponding to nightlies.

## Creating your own applications

### Publish your application

In order for your application to be install-able via `install`, it should
be published to Maven / Ivy repositories.

Once published, you should be able to pull its class path via the
[`fetch` command](cli-fetch.md):
```bash
$ cs fetch my-app-org::my-app-name:latest.release
…
```
Replace `my-app-org` and `my-app-name` by the Maven group id and
artifact id of your application. Note the use of `::` to separate
them if your application corresponds to a Scala module. Use a simple
`:` for Java modules. `:::` is also accepted for fully cross-versioned
Scala modules.

Pass any missing Maven / Ivy repository via the `-r` / `--repository`
option.

### Run your application via `bootstrap`

You should then check that your application runs fine via
the [`bootstrap` command](cli-bootstrap.md):
```bash
$ cs bootstrap my-app-org::my-app-name:latest.release -o my-app
$ ./my-app # should run your application
…
```

Adjust the dependency string as above, and pass any required repository.

Pass `-M my.main.class` to `bootstrap` if the main class of your application
isn't detected automatically, or if the wrong main class is detected.

### Create an application descriptor

You should then put the details passed to the `bootstrap` command in
an [application descriptor](#application-descriptor-reference).
Examples of such application descriptors live in
[the default channel GitHub repository](https://github.com/coursier/apps/tree/master/apps/resources).

At the minimum, your application descriptor should have
`repositories` and `dependencies` sections, like
```json
{
  "repositories": [
    "central"
  ],
  "dependencies": [
    "my-app-org::my-app-name:latest.release"
  ]
}
```
Add a `"mainClass": "my.main.class"` field if you passed an explicit main
class to the `bootstrap` command.

If your application should be run via `my-app` once installed, its
application descriptor should be written in a file named `my-app.json`.

Check that your application runs fine via its application descriptor by installing
it to a temporary directory with
```bash
$ cs install \
    --install-dir tmp-install \
    --default-channels=false \
    --channel directory/of/app/descriptor \
    my-app
$ tmp-install/my-app # should run your application
…
```

Replace `directory/of/app/descriptor` by the directory where you wrote
your application descriptor (`my-app.json` in the example here).
Replace `my-app` by your application name.

### Publish your application descriptor

You should then make your application descriptor available to your users.
You can either:
- add it to an existing channel, such as the contrib channel, or
- create your own channel.

#### Use the contrib channel

Send a pull requests adding your application channel
under the [resources directory](https://github.com/coursier/apps/tree/master/apps-contrib/resources)
of the contrib channel.
Once the maintainers of this repository merge your pull request and cut a release,
your users should be able to install and run your appplication like
```bash
$ cs install --contrib my-app
$ my-app
…
```

#### Create your own channel

If you wish to create your own channel,
You can either create a [JAR-based channel](#jar-based-channels) or
a [URL-based channel](#url-based-channels).

For the sake of simplicity, we'll only describe URL-based channels here.

Create a JSON file, containing a JSON object with your application name
as key, and your application descriptor as value, like
```json
{
  "my-app": {
    "repositories": [
      "central"
    ],
    "dependencies": [
      "my-app-org::my-app-name:latest.release"
    ]
  }
}
```

Put that file on at a URL where your users can consume it.
It can be a file in a GitHub repository, or a GitHub gist, but
any service allowing to download that file via HTTP should work.
Don't forget
to give your users the link to the "raw" content of your channel.

Once that file is available at some URL, like `https://git.io/Jv8um`, your
users should be able to install your application with
```bash
$ cs install --channel https://git.io/Jv8um my-app
$ my-app
…
```

## Channel types

Channels consist either of
- [a single JSON file, available at some URL](#url-based-channels), or
- [a collection of JSON files in a JAR](#jar-based-channels), published on a Maven repository, or
- [a local directory, containing JSON files](#directory-based-channels).

For example, the default channel, `io.get-coursier:apps` is JAR-based.
It lives in [this GitHub repository](https://github.com/coursier/apps).
It is published as `io.get-coursier:apps`
[on Maven Central](https://repo1.maven.org/maven2/io/get-coursier/apps).

Its JAR contains a number of JSON files at its root:
```
$ unzip -l "$(cs fetch io.get-coursier:apps:0.0.8)" | grep json
      188  02-09-2020 17:48   ammonite.json
      175  02-09-2020 17:48   coursier.json
      332  02-09-2020 17:48   cs.json
      241  02-09-2020 17:48   dotty-repl.json
      150  02-09-2020 17:48   echo-graalvm.json
      108  02-09-2020 17:48   echo-java.json
      172  02-09-2020 17:48   echo-native.json
      335  02-09-2020 17:48   giter8.json
      135  02-09-2020 17:48   mdoc.json
      323  02-09-2020 17:48   mill-interactive.json
      321  02-09-2020 17:48   mill.json
      524  02-09-2020 17:48   sbt-launcher.json
      222  02-09-2020 17:48   scala.json
      209  02-09-2020 17:48   scalac.json
      213  02-09-2020 17:48   scaladoc.json
      180  02-09-2020 17:48   scalafix.json
      184  02-09-2020 17:48   scalafmt.json
      204  02-09-2020 17:48   scalap.json
```

### JAR-based channels

These channels can be created by publishing a JAR to a Maven or Ivy repository.
The default channel, `io.get-coursier:apps`,
living in [this GitHub repository](https://github.com/coursier/apps),
is [published](https://repo1.maven.org/maven2/io/get-coursier/apps) this way.

[The JAR it publishes](https://repo1.maven.org/maven2/io/get-coursier/apps/0.0.8/apps-0.0.8.jar)
contains JSON files at its root (`scala.json`, `ammonite.json`, etc.)
Each of these JSON files gives the Maven coordinates of the corresponding
application, plus other parameters, like Java properties that need to be set
for the application to start fine, an explicit main class if the manifest
of the application JAR doesn't contain one or if this JAR has several main
classes to choose from, etc. See [this section](#application-descriptor-reference)
for more details.

JAR-based channels can be passed to the `--channel` option of `install` command,
via their Maven coordinates, with no version, like `io.get-coursier:apps`.
The latest version of the channel module is automatically pulled.

The following command explicitly adds the default channel via its coordinates:
```bash
$ cs install --default-channels=false \
    --channel io.get-coursier:apps \
    mdoc
```

JAR-based channels should be the most reliable kind of channels for end-users
(if published on Central, the channel JARs can't a priori be deleted, and previous
versions can be manually inspected if needed), but are the less straightforward
to setup.

[This giter8 template](https://github.com/coursier/coursier-channel.g8) facilitates
and describes how to create your own JAR-based channel.

### URL-based channels

These channels consist in a single JSON file, containing a JSON object.
This object's keys correspond to application names, and its values are
application descriptors.

Such a file must be made available at a public URL. This URL can then be passed
to the `--channel` option of the `install` command.

For example, the [following URL](https://gist.githubusercontent.com/alexarchambault/5f6c8c25997063a07ea769d7a2b61466/raw/3aadef1c006777bba024f492a28da8d6698ee6a0/demo-channel.json)
contains two application descriptors, for `"my-scala"` and `"my-scalac"`, that can
be installed via
```bash
$ cs install --channel https://gist.githubusercontent.com/alexarchambault/5f6c8c25997063a07ea769d7a2b61466/raw/3aadef1c006777bba024f492a28da8d6698ee6a0/demo-channel.json my-scala
$ my-scala --help
…
```

These channels are the easiest to setup, yet don't provide the ability to
inspect former versions out-of-the-box.

### Directory-based channels

These channels consist in a single local directory, containing JSON files at
its root. Each of these JSON files correspond to an application descriptor.
For example, JSON file `foo.json` contains the application descriptor for
application `foo`.

You get [an example](https://github.com/coursier/apps/tree/c2645a5e4768b5b58d29902e18630e9d9bf6eacd/apps/resources)
of such a directory if you clone the default channel GitHub repository:
```bash
$ git clone https://github.com/coursier/apps.git
$ cs install --default-channels=false \
    --channel ./apps/apps/resources \
    mdoc
```

These channels are intended for local usage and debugging mostly.

## Application metadata

When installing an application with `cs install`, the `install` command puts
a self-executable JAR for that application in the application directory. It
puts some metadata of its own in this JAR too, under `META-INF/coursier/`.

These metadata keep track of:
- the channel used to go from the application name to an application descriptor, and the repositories used to
fetch this channel JAR if it is JAR-based,
- the application descriptor itself, that gives the application dependencies, and other parameters to
start the application (see [below](#application-descriptor-reference) too),
- the list of JARs used by this application, along with their checksums.

Updates:
- check the channel for updates of the application descriptor, and
- check that the resolved artifacts of the application descriptor didn't change.

If any of these two changed, the launcher of the application is generated again, from the newest data.

## Application descriptor reference

Application descriptors are parsed from [`RawAppDescriptor.scala`](https://github.com/coursier/coursier/blob/master/modules/install/src/main/scala/coursier/install/RawAppDescriptor.scala),
that contains the full up-to-date list of supported fields.

Unrecognized fields in an application descriptor are simply ignored.

#### `repositories`

Specifies repositories this application should be pulled from. Accepts the same inputs
as the `-r` / `--repository` option of the coursier CLI.

Example:
```json
"repositories": [
  "central",
  "typesafe:ivy-releases",
  "https://some.custom.maven/repository"
]
```

#### `dependencies`

The dependency list of this application. Can contain Java dependencies (`org:name:version`),
binary or fully cross-versioned Scala dependencies (`org::name:version`, `org:::name:version`),
and Scala platform specific dependencies (for Scala Native typically, like `org::name::version`).

Example
```json
"dependencies": [
  "org.scalameta::mdoc:latest.stable"
]
```

#### `launcherType`

Specifies the type of launcher that should be built for this application. Can be one of

- `bootstrap`: builds a bootstrap as built by the `bootstrap` command by default
- `assembly`: builds an assembly (corresponds to the `--assembly` option of the `bootstrap` command)
- `standalone`: builds a bootstrap embedding its JAR dependencies as resources (corresponds to the `--standalone` option of the `bootstrap` command - these JARs are similar to the ones [one-jar](http://one-jar.sourceforge.net) builds)
- `scala-native`: builds a Scala Native application (requires the right [environment setup](https://scala-native.readthedocs.io/en/v0.3.9-docs/user/setup.html), and requires coursier to be started via its [JAR-based launcher](cli-installation.md#jar-based-launcher) for now)
- `graalvm-native-image`: builds a GraalVM native image
- `prebuilt`: points to an existing prebuilt executable (requires the [`prebuilt`](#prebuilt) field to be set as well)

#### `mainClass`

Specifies an explicit main class to start. Add a `?` suffix to use the specified main class
only if none is found in the application JAR manifest.

Examples
```json
"mainClass": "scala.tools.nsc.MainGenericRunner"
```

```json
"mainClass": "ammonite.Main?"
```

#### `name`

Specifies the name under which this application should be installed. The default name is the same as
the JSON file (`foo.json` gets installed as `foo`). The `name` field allows to override that.

Example
```json
"name": "amm"
```

#### `prebuilt`

Specifies a URL at which prebuilt versions of the application are available.

This field is only used if [`launcherType`](#launchertype) is `scala-native` or `graalvm-native-image`.

That URL can contain expressions like `${version}` and `${platform}`, that are replaced by the
selected version of the application, and the platform it is installed in. Currently `${platform}` can be
replaced by one of

- `x86_64-pc-linux`
- `x86_64-apple-darwin`
- `x86_64-pc-win32`
- `aarch64-pc-linux`
- `aarch64-apple-darwin`

Example
```json
"prebuilt": "https://github.com/coursier/coursier/releases/download/v${version}/cs-${platform}"
```
For version `2.0.0-RC6-6` on macOS, this gets transformed as
[this URL](https://github.com/coursier/coursier/releases/download/v2.0.0-RC6-6/cs-x86_64-apple-darwin).

#### `prebuiltBinaries`

Like `prebuilt`, specifies the URLs at which prebuilt versions of the application are available, but
URLs are specific to platforms.

Example

~~~ json
"prebuiltBinaries": {
  "x86_64-apple-darwin": "gz+https://github.com/coursier/coursier/releases/download/v${version}/cs-x86_64-apple-darwin.gz",
  "x86_64-pc-win32": "zip+https://github.com/coursier/coursier/releases/download/v${version}/cs-x86_64-pc-win32.zip"
}
~~~
For version `2.1.0` on Windows, this gets transformed as
[this URL](https://github.com/coursier/coursier/releases/download/v2.1.0/cs-x86_64-pc-win32.zip).

The possible values for platforms are listed in the previous section.
