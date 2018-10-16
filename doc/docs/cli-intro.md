---
title: Intro
---

## Installation

### curl

Download and run its launcher with
```
$ curl -L -o coursier https://git.io/coursier &&
    chmod +x coursier &&
    ./coursier --help
```

The launcher itself weighs only 30 kB and can be easily embedded as is in other projects.
It downloads the artifacts required to launch coursier on the first run.

The short URL [`https://git.io.coursier`](https://git.io.coursier) redirects to
[`https://github.com/coursier/coursier/raw/master/coursier`](https://github.com/coursier/coursier/raw/master/coursier).
Since version `1.0.0-RC1`, `master` in the latter URL can be replaced by the
tag of a specific version, to download the launcher of that exact version, like
[`https://github.com/coursier/coursier/raw/v1.1.0-M7/coursier`](https://github.com/coursier/coursier/raw/v1.1.0-M7/coursier).

### brew

Alternatively on OS X, install it via homebrew, that puts the `coursier` launcher directly in your PATH,
```
$ brew tap coursier/formulas
$ brew install --HEAD coursier/formulas/coursier
```

### Arch Linux

Install it from [AUR](https://aur.archlinux.org/packages/coursier/),
```
$ pacaur -S coursier
```

### zsh completions

If you use ZSH, simple tab-completions are available by copying the
[`scripts/_coursier`](https://raw.githubusercontent.com/coursier/coursier/master/scripts/_coursier)
file into your completions directory, if you have one. If
you do not, then you can install the completions with,
```
mkdir -p ~/.zsh/completion
cp scripts/_coursier ~/.zsh/completion/
echo 'fpath=(~/.zsh/completion $fpath)' >> ~/.zshrc
echo 'autoload -Uz compinit ; compinit' >> ~/.zshrc
```

## Usage

```
$ ./coursier --help
```
lists the available coursier commands. The most notable ones are `launch`, and `fetch`. Type
```
$ ./coursier command --help
```
to get a description of the various options the command `command` (replace with one
of the above command) accepts.

Both commands below can be given repositories with the `-r` or `--repository` option, like
```
-r central
-r https://oss.sonatype.org/content/repositories/snapshots
-r "ivy:https://repo.typesafe.com/typesafe/ivy-releases/[organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]"
```

`central` and `ivy2local` correspond to Maven Central and `~/.ivy2/local`. These are used by default
unless the `--no-default` option is specified.

Repositories starting with `ivy:` are assumed to be Ivy repositories, specified with an Ivy pattern, like `ivy:https://repo.typesafe.com/typesafe/ivy-releases/[organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]`.
Else, a Maven repository is assumed.

To set credentials for a repository, pass a user and password in its URL, like
```
-r https://user:pass@nexus.corp.com/content/repositories/releases
```
