---
title: Overview
hide_title: true
---

# CLI

The CLI of coursier allows to
- [list the transitive dependencies](cli-resolve.md) and
- [fetch the artifacts](cli-fetch.md) of one or more dependencies,
- [run applications from dependencies](cli-launch.md), and
- [generate convenient launchers to run them](cli-bootstrap.md)
among others.

See below for its [installation](#native-launcher), [its basic usage](#usage),
and click on the links above or on the left for more details about each of
its commands.

## Native launcher

> The commands below install the native launcher as `cs`, which can be
substituted to `coursier` in the various examples on this website.

### Linux

On Linux, download and run the coursier launcher with
```bash
$ curl -Lo cs https://git.io/coursier-cli-linux &&
    chmod +x cs &&
    ./cs --help
```

### macOS

Download and run the coursier launcher with
```bash
$ curl -Lo cs https://git.io/coursier-cli-macos &&
    chmod +x cs &&
    xattr -d com.apple.quarantine cs &&
    ./cs --help
```

Note the `xattr` command to circumvent notarization on macOS Catalina.

Alternatively, the native launcher can be installed via [homebrew](https://brew.sh) with
```bash
$ brew install coursier/formulas/coursier
$ cs --help
```

### Windows

On Windows, use
```bash
> bitsadmin /transfer cs-cli https://git.io/coursier-cli-windows-exe "%cd%\cs.exe"
> .\cs --help
```

## JAR-based launcher

### Linux / macOS

In case you run into any issue with the [native launcher](#native-launcher),
a JAR-based launcher is available.

Download and run it with
```bash
$ curl -Lo coursier https://git.io/coursier-cli &&
    chmod +x coursier &&
    ./coursier --help
```

Note that the JAR-based launcher requires Java 8 or later to run.
That is, a command like `java -version` should print a version >= 8.

The JAR-based launcher weights only about 25 kB and can be easily embedded
as is in other projects.
It downloads the artifacts required to launch coursier on the first run.

Alternatively, on macOS, the JAR-based launcher can be installed via [homebrew](https://brew.sh) with
```bash
$ brew install coursier/formulas/coursier
$ coursier --help
```

### Windows

Install and run the JAR-based coursier launcher from the current directory at the Windows prompt, with
```bat
> bitsadmin /transfer downloadCoursierCli https://git.io/coursier-cli "%cd%\coursier"
> bitsadmin /transfer downloadCoursierBat https://git.io/coursier-bat "%cd%\coursier.bat"
```

You can then run coursier from the same directory, like
```bat
> coursier resolve io.circe:circe-core_2.12:0.10.0
```

### Arch Linux

Install it from [AUR](https://aur.archlinux.org/packages/coursier/),
```bash
$ pacaur -S coursier
```

### FreeBSD

Install it via `pkg` from the [Ports Collection](https://www.freshports.org/devel/coursier/),
```bash
$ pkg install coursier
```

### zsh completions

If you use ZSH, simple tab-completions are available by copying the
[`scripts/_coursier`](https://raw.githubusercontent.com/coursier/coursier/master/scripts/_coursier)
file into your completions directory, if you have one. If
you do not, then you can install the completions with,
```bash
mkdir -p ~/.zsh/completion
cp scripts/_coursier ~/.zsh/completion/
echo 'fpath=(~/.zsh/completion $fpath)' >> ~/.zshrc
echo 'autoload -Uz compinit ; compinit' >> ~/.zshrc
```

## Usage

```bash
$ ./coursier --help
```
lists the available coursier commands. The most notable ones are `launch`, and `fetch`. Type
```bash
$ ./coursier command --help
```
to get a description of the various options the command `command` (replace with one
of the above command) accepts.

Both commands below can be given repositories with the `-r` or `--repository` option, like
```bash
-r central
-r sonatype:snapshots
-r https://oss.sonatype.org/content/repositories/snapshots
-r "ivy:https://repo.typesafe.com/typesafe/ivy-releases/[organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]"
```

`central` and `ivy2local` correspond to Maven Central and `~/.ivy2/local`. These are used by default
unless the `--no-default` option is specified.

Repositories starting with `ivy:` are assumed to be Ivy repositories, specified with an Ivy pattern, like `ivy:https://repo.typesafe.com/typesafe/ivy-releases/[organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]`.
Else, a Maven repository is assumed.

To set credentials for a repository, pass a user and password in its URL, like
```bash
-r https://user:pass@nexus.corp.com/content/repositories/releases
```

## Launcher URLs

### Latest launchers

The various [git.io](https://git.io) shortened URLs above redirect
to the latest launchers from the [launchers repository](https://github.com/coursier/launchers):

|OS|Short URL|redirects to|
|-|-|-|
|Linux|<https://git.io/coursier-cli-linux>|<https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-linux>|
|macOS|<https://git.io/coursier-cli-macos>|<https://github.com/coursier/launchers/raw/master/cs-x86_64-apple-darwin>|
|Windows|<https://git.io/coursier-cli-windows-exe>|<https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-win32.exe>|
|Any (needs JVM)|<https://git.io/coursier-cli>|<https://github.com/coursier/coursier/raw/gh-pages/coursier> (gh-pages branch of coursier repository rather than launchers repository)|
|Any (needs JVM)| |<https://github.com/coursier/launchers/raw/master/coursier> (same launcher as above)|

### Specific versions

To download specific versions of the launcher, download them from GitHub release
assets:

|OS|URL|Since version|
|-|-|-|
|Linux|<https://github.com/coursier/coursier/releases/download/v@VERSION@/cs-x86_64-pc-linux>|`2.0.0-RC3-1`|
|macOS|<https://github.com/coursier/coursier/releases/download/v@VERSION@/cs-x86_64-apple-darwin>|`2.0.0-RC3-1`|
|Windows|<https://github.com/coursier/coursier/releases/download/v@VERSION@/cs-x86_64-pc-win32.exe>|`2.0.0-RC6`|
|Any (needs JVM)|<https://github.com/coursier/coursier/releases/download/v@VERSION@/coursier>|`1.1.0-M9`|
|Any (needs JVM)|<https://github.com/coursier/coursier/raw/v1.1.0-M9/coursier> (up to version `1.1.0-M9`)|*Invalid for newest versions*|
