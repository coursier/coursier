---
title: install
---

The `install` command installs Scala applications in the
[installation directory](#installation-directory)
configured when installing `cs` (`~/.local/share/coursier/bin` by default on Linux):

```bash
$ cs install scalafmt
$ scalafmt --version
scalafmt 3.0.6
```

You may give a specific version number as follows:

```bash
$ cs install scalafmt:2.4.2
$ scalafmt --version
scalafmt 2.4.2
```

This can be used to install a specific version of Scala itself:

```bash
$ cs install scala:2.12.15 scalac:2.12.15
$ scala -version
Scala code runner version 2.12.15 -- Copyright 2002-2021, LAMP/EPFL and Lightbend, Inc.
```

If the installation directory is not already on your `PATH`, you may add it for the current session with
```bash
$ eval "$(cs install --env)" # add installation directory in PATH in the current session
```

[The `search` command](#search) can help you find the application you are looking for:
```bash
$ cs search fmt
scalafmt
```

[The `update` command](#update) updates installed applications if newer versions are available:
```bash
$ cs update scala
```

Running `cs update` with no argument updates all the installed applications:
```bash
$ cs update
```

[The `uninstall` command](#uninstall) allows to uninstall it:
```bash
$ cs uninstall scala
$ scala
scala: command not found
```

[The `list` command](#list) allows you to view all of your currently installed
applications:

```bash
$ cs list
amm
coursier
cs
sbt
sbtn
scala
scalac
scalafmt
```

## Java Options

One can pass Java arguments to the installed applications:
- by prefixing Java options with `-J`, and / or
- by setting the `JAVA_OPTS` environment variable.

For example
```text
$ cs install scala
$ scala -J-Xmx2g -J-Dfoo=bar -J-Dvalue=123
…
scala> sys.props("foo")
val res0: String = bar
…
$ JAVA_OPTS="-Xmx2g -Dfoo=bar -Dvalue=123" scala
…
scala> sys.props("value")
val res0: String = 123
…
```

Both kinds of options can be mixed, like
```text
$ JAVA_OPTS="-Xmx2g -Dfoo=bar" scala -J-Dvalue=123
…
scala> sys.props("foo")
val res0: String = bar

scala> sys.props("value")
val res1: String = 123
…
```

## Search

Search for the application you are looking for with `cs search`:
```bash
$ cs search fmt
scalafmt
```

## Update

Update specific applications with `cs update`, like
```bash
$ cs update coursier
```

Update all installed applications at once with
```bash
$ cs update
```

## Uninstall

The `uninstall` command uninstalls applications previously installed
by the `install` command.

Pass it the application names of applications to uninstall, like
```bash
$ cs uninstall scala scalac
```

It also accepts a `--all` option to uninstall all applications
from the installation directory.
```bash
$ cs uninstall --all
```

> The above command uninstalls `cs` itself as well!
> If you need to reinstall it, use the [installation instructions](cli-installation.md) again.

Note that the `uninstall` command only uninstalls applications previously
installed by the `install` command, not files put in the installation
directory by other means. It checks if a file was installed by
the `install` command by looking for [its metadata](cli-appdescriptors.md#application-metadata) in it.

Note also that some applications have a different name when installed
and when used and uninstalled:
```bash
$ cs install ammonite
$ amm
…
$ cs uninstall amm # ammonite is installed as "amm"
```

## List

The `list` command lists all the installed applications. By default this will
list the applications in your [default installation
directory](#installation-directory). However, you can explicitly provide a
different directory with the `--dir` flag if you have your install directory in
a custom location.

```bash
$ cs list --dir /myCustomDirectory
```

## Channels

Application descriptors are pulled via "channels": repositories listing available applications and how they must be installed.

By default, coursier uses the Main channel `io.get-coursier:apps`.
It can be disabled with the `--default-channels=false` option.

There also exists a Contrib channel `io.get-coursier:apps-contrib`, which is not used by default, and can be enabled with `--contrib`.
For example:

```bash
$ cs install --contrib proguard
```

Both of those are [defined on GitHub](https://github.com/coursier/apps), where you can see the full list of applications that they contain.
Feel free to send pull requests to add applications to the Contrib channel.

Custom channels can be provided with `--channel <custom-channel>`.
This is useful if you [create your own application descriptors](cli-appdescriptors.md).

## Installation directory

Applications are installed in OS-specific directories.
These respect the
["XDG Base directory specification"](https://specifications.freedesktop.org/basedir-spec/basedir-spec-latest.html)
on Linux.

The default application directory is:
- Linux: `~/.local/share/coursier/bin`
- macOS: `~/Library/Application Support/Coursier/bin`
- Windows:

The actual installation directory is computed via:
- the `COURSIER_BIN_DIR` environment variable if it's defined, else
- the `COURSIER_INSTALL_DIR` environment variable if it's defined, else
- the `coursier.install.dir` Java property if it's defined,
- else, the default OS-specific directory above is used.

Note that the `install`, `update`, `uninstall`, and `setup` commands also
accept a directory via `--dir` or `--install-dir`, like
```bash
$ cs install ammonite --install-dir test-install-dir
$ test-install-dir/ammonite
…
```
The directory passed this way takes precedence over the ones above.

One can run `cs install --setup` to [update](https://get-coursier.io/docs/cli-installation.html#how-it-sets-environment-variables-globally) 
profile files (Linux / macOS) or user environment variables (Windows),
to add the installation directory to `PATH`.
