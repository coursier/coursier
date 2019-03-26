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

See below for its [installation](#installation), [its basic usage](#usage),
and click on the links above or on the left for more details about each of
its commands.

## Installation

### curl

Download and run its launcher with
```bash
$ curl -Lo coursier https://git.io/coursier-cli &&
    chmod +x coursier &&
    ./coursier --help
```

The launcher itself weights only 17 kB and can be easily embedded as is in other projects.
It downloads the artifacts required to launch coursier on the first run.

The short URL <https://git.io/coursier-cli> redirects to
<https://github.com/coursier/coursier/raw/gh-pages/coursier>.
From version `1.0.0-RC1` to `1.1.0-M9`, `gh-pages` in the latter URL can be replaced by the
tag of a specific version, to download the launcher of that exact version, like
<https://github.com/coursier/coursier/raw/v1.1.0-M7/coursier>. For versions `1.1.0-M9` and later,
the launcher is available as an asset on the GitHub release page, e.g.
<https://github.com/coursier/coursier/releases/download/v1.1.0-M9/coursier>.

### brew

Alternatively on OS X, install it via homebrew, that puts the `coursier` launcher directly in your PATH,
```bash
$ brew tap coursier/formulas
$ brew install coursier/formulas/coursier
```

### Windows

Install and run coursier from the current directory at the Windows prompt, with
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
