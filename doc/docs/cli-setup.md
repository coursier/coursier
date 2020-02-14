---
title: setup
---

The `setup` command aims at making it easier to setup a machine for
Scala development, be it your own development machine, or CI
environments.

It currently ensures that:
- a JVM is installed on your system,
- standard Scala CLI tools are installed.

## Interactive mode

Launched without options, `setup` checks that a JVM and the standard Scala CLI tools are
installed on your system, and updates your profile files (Linux / macOS) or user environment variables
(Windows).

```bash
$ cs setup
```

It asks you to confirm prior to performing any of these actions.

To answer yes to all these questions beforehand, pass `--yes` or `-y` to `setup`:
```bash
$ cs setup --yes
```

On Windows, simply launching `cs` invokes the `setup` command. That allows simply
double-clicking on `cs.exe` from the Windows explorer to open a terminal running the `setup` command.

## Non-interactive mode

If you prefer the `setup` command not to update your profile files (`~/.profile` and the like),
pass `--env` to it, and call `eval` (from bash or zsh) on its output:
```bash
$ eval "$(cs setup --env)"
```
This updates `JAVA_HOME` and `PATH` for the duration of the current session.

Pass it a JVM id and a list of applications to
- use that JVM in the current session, and
- ensure some applications are installed,
like
```bash
$ eval "$(cs setup --env --jvm 11 --apps sbt-launcher,ammonite)"
$ sbt
…
$ amm
…
$ java -version
openjdk version "11.0.6" 2020-01-14
OpenJDK Runtime Environment AdoptOpenJDK (build 11.0.6+10)
OpenJDK 64-Bit Server VM AdoptOpenJDK (build 11.0.6+10, mixed mode)
```

The `setup` command currently doesn't offer a way to revert the changes
it made to the environment variables of the current session. It's mostly
made to easily setup CI environments, rather than switch JVMs. If you're
interested in trying / switching JVM, see the
[`--env` and `--disable` options](cli-java.md#environment-variables) of the
[`java` command](cli-java.md).

## Overriding defaults

### JVM

The JVM installed if [none is found on your system](cli-java.md#system-jvm-detection)
is the same as the [`java` command](cli-java.md), the latest AdoptOpenJDK 8 as of
writing this.

Pass `--jvm` to ignore the already installed jvm and install a custom one:
```bash
$ cs setup --jvm 11
```

### JVM directory

JVMs are extracted in
[the JVM cache directory](cli-java.md#managed-jvm-directory) by default.

Pass a custom directory to extract JVM under with `--jvm-dir`:
```bash
$ eval "$(cs setup --jvm 11 --jvm-dir test-jvm)"
…
$ echo "$JAVA_HOME"
…/test-jvm/adopt@1.11.0-6
```

### Applications

The `setup` command installs a number of standard Scala CLI applications by default.
Pass a custom list of applications to install instead with `--apps`.

```bash
$ cs setup --apps sbt-launcher,ammonite
```

`--apps` can be specified multiple times, and expects a `,`-separared list of applications.

See the documentation of the [`install` command](cli-install.md) for more details about
where these applications are defined, how to add your own, etc.

### Application directory

Applications are installed in [the installation directory of coursier](cli-install.md#installation-directory)
by default.

Pass a custom directory to install applications in with `--install-dir`:
```bash
$ eval "$(cs setup --apps sbt-launcher,ammonite --install-dir tmp-install)"
…
$ tmp-install/sbt
…
$ tmp-install/amm
…
```

### Profile files directory

Pass a custom directory that contains `.profile` / `.bash_profile` / `.zprofile` files with:
```bash
$ cs setup --user-home test-home
…
$ cat test-home/.profile

# >>> coursier install directory >>>
export PATH="$PATH:/Users/alex/Library/Application Support/Coursier/bin"
# <<< coursier install directory <<<
```

## How it sets environment variables globally

### Linux / macOS

The `setup` command updates the following files:
- `~/.profile` (created it if needed),
- `~/.zprofile` if zsh is the current shell (created if needed, respects `ZDOTDIR`),
- `~/.bash_profile` (only if it exists).

For example, if `~/.bash_profile` doesn't exist and you're using zsh, both `~/.profile`
and `~/.zprofile` will be updated (and created if needed).

The sections the `setup` command adds to your profile files are clearly delimited, like
```bash
# >>> coursier install directory >>>
export PATH="$PATH:/Users/alex/Library/Application Support/Coursier/bin"
# <<< coursier install directory <<<
```

### Windows

On Windows, the `setup` command updates the `User` environment variables.
