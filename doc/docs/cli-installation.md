---
title: Installation
---

These instructions will install the coursier CLI `cs` itself, as well as a typical Scala development environment.
By default, they will install the following applications:

- `cs` itself, to further manage your Scala environment
- `scala-cli`, a [convenient tool to compile / run / package Scala code](https://scala-cli.virtuslab.org)
- `scala`, the Scala REPL
- `scalac`, the Scala compiler
- `sbt` and `sbtn`, the [sbt build toold](https://www.scala-sbt.org/)
- `ammonite`, [an enhanced REPL](https://ammonite.io/) for Scala
- `scalafmt`, the [Scala code formatter](https://scalameta.org/scalafmt/)

They will also install a JVM if none is found on the system.

If you want more control over what gets installed and how, please check out
the [Command-line options](#command-line-options) section.

After the setup, you can [start using Scala](https://docs.scala-lang.org/scala3/getting-started.html#create-a-hello-world-project-with-sbt), or install more applications with the [`install`](cli-install.md) command.

## Native launcher

### Linux

On Linux, download the coursier installer with

```bash
# On x86-64 (aka AMD64)
$ curl -fL "https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-linux.gz" | gzip -d > cs
# On ARM64
$ curl -fL "https://github.com/VirtusLab/coursier-m1/releases/latest/download/cs-aarch64-pc-linux.gz" | gzip -d > cs
```

Then, run it with

```bash
$ chmod +x cs
$ ./cs setup
```

Other flavors of native Linux launchers are available, see [below](#other-native-linux-launchers).

### macOS

On macOS, download and run the coursier installer with

```bash
# On Apple Silicon (M1, M2, ...):
$ curl -fL https://github.com/VirtusLab/coursier-m1/releases/latest/download/cs-aarch64-apple-darwin.gz | gzip -d > cs
# Otherwise:
$ curl -fL https://github.com/coursier/launchers/raw/master/cs-x86_64-apple-darwin.gz | gzip -d > cs
$ chmod +x cs
$ ./cs setup
```

### macOS - brew based installation

Alternatively, the coursier launcher can be installed via [homebrew](https://brew.sh) with
```bash
$ brew install coursier/formulas/coursier
$ cs setup
```

### Windows

> For ARM64 Windows users, please follow **JAR-based launcher** section. The Windows installer currently only installs x86_64 build of Coursier.

On Windows, [download and execute the Windows installer](https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-win32.zip).

If you prefer a command line-based install, or if you would like to customize the setup options, use:

```pwsh
# PowerShell
Invoke-WebRequest -Uri "https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-win32.zip" -OutFile "cs-x86_64-pc-win32.zip"
Expand-Archive -Path "cs-x86_64-pc-win32.zip" -DestinationPath .
Rename-Item -Path "cs-x86_64-pc-win32.exe" -NewName "cs.exe"
Remove-Item -Path "cs-x86_64-pc-win32.zip"
.\cs setup
```

```bat
:: CMD
curl -fLo cs-x86_64-pc-win32.zip https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-win32.zip
tar -xf cs-x86_64-pc-win32.zip
move cs-x86_64-pc-win32.exe cs.exe
.\cs setup
```

### Check your setup

Check your setup with

```bash
$ scala -version
Scala code runner version 2.13.7 -- Copyright 2002-2021, LAMP/EPFL and Lightbend, Inc.
```

If that does not work, you may need to log out and log back in (or reboot) in order for the changes to take effect.

## JAR-based launcher

In case you run into any issue with the [native launcher](#native-launcher),
a JAR-based launcher is available.

> The commands below install the JAR-based launcher as `coursier`, which can be
substituted to `cs` in the various examples on this website.

### Linux / macOS

Download and run the launcher with
```bash
$ curl -fLo coursier https://github.com/coursier/launchers/raw/master/coursier &&
    chmod +x coursier &&
    ./coursier
```

Note that the JAR-based launcher requires Java 8 or later to run.
That is, a command like `java -version` should print a version >= 8.

The JAR-based launcher weights only about 25 kB and can be easily embedded
as is in other projects.
It downloads the artifacts required to launch coursier on the first run.

Alternatively, on macOS, the JAR-based launcher can be installed via [homebrew](https://brew.sh) with
```bash
$ brew install coursier/formulas/coursier
$ coursier
```

### Windows

Install and run the JAR-based coursier launcher from the current directory at the Windows prompt, with
```bat
# CMD
> bitsadmin /transfer downloadCoursierCli https://github.com/coursier/launchers/raw/master/coursier "%cd%\coursier"
> bitsadmin /transfer downloadCoursierBat https://github.com/coursier/launchers/raw/master/coursier.bat "%cd%\coursier.bat"

# PowerShell
> Start-BitsTransfer -Source https://github.com/coursier/launchers/raw/master/coursier -Destination coursier
> Start-BitsTransfer -Source https://github.com/coursier/launchers/raw/master/coursier.bat -Destination coursier.bat
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

## zsh completions

If you use ZSH, simple tab-completions are available by writing the
coursier completion data into your completions directory.
You can install the completions with:
```bash
mkdir -p ~/.zsh/completion
```
```bash
echo '#compdef _cs cs

function _cs {
  eval "$(cs complete zsh-v1 $CURRENT $words[@])"
}' > ~/.zsh/completion/_cs
```
```bash
echo 'fpath=(~/.zsh/completion $fpath)' >> ~/.zshrc
echo 'autoload -Uz compinit ; compinit' >> ~/.zshrc
```

## Launcher URLs

### Latest launchers

|OS|URL|
|-|-|
|Linux|<https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-linux.gz>|
|macOS|<https://github.com/coursier/launchers/raw/master/cs-x86_64-apple-darwin.gz>|
|Windows|<https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-win32.zip>|
|Linux (ARM64)|<https://github.com/coursier/launchers/raw/master/cs-aarch64-pc-linux.gz>|
|Any (needs JVM)|<https://github.com/coursier/coursier/raw/gh-pages/coursier> (gh-pages branch of coursier repository rather than launchers repository)|
|Any (needs JVM)|<https://github.com/coursier/launchers/raw/master/coursier> (same launcher as above)|

> Note that most launchers used to be available at short `git.io` URLs, and at long URLs
*without* compression, that is not ending in `.gz` nor `.zip`.
>
> These URLs are not updated anymore, and
point at former coursier versions, as using compressed launchers
allows to save space and bandwidth (for the long URLs), and as git.io is [about to be
deprecated](https://github.blog/changelog/2022-01-11-git-io-no-longer-accepts-new-urls).

### Specific versions

To download specific versions of the launcher, download them from GitHub release
assets:

|OS|URL|Since version|
|-|-|-|
|Linux|<https://github.com/coursier/coursier/releases/download/v@VERSION@/cs-x86_64-pc-linux.gz>|`2.0.16-158-gbdc8669f9`|
|macOS|<https://github.com/coursier/coursier/releases/download/v@VERSION@/cs-x86_64-apple-darwin.gz>|`2.0.16-158-gbdc8669f9`|
|Windows|<https://github.com/coursier/coursier/releases/download/v@VERSION@/cs-x86_64-pc-win32.exe>|`2.0.16-158-gbdc8669f9`|
|Linux (ARM64)|<https://github.com/coursier/coursier/releases/download/v@VERSION@/cs-aarch64-pc-linux.gz>|`2.0.16-158-gbdc8669f9`|
|Any (needs JVM)|<https://github.com/coursier/coursier/releases/download/v@VERSION@/coursier>|`1.1.0-M9`|

Former URLs, for information:

|OS|URL|Since version|Up to version|
|-|-|-|-|
|Linux|<https://github.com/coursier/coursier/releases/download/v@VERSION@/cs-x86_64-pc-linux>|`2.0.0-RC3-1`|`2.0.16`|
|macOS|<https://github.com/coursier/coursier/releases/download/v@VERSION@/cs-x86_64-apple-darwin>|`2.0.0-RC3-1`|`2.0.16`|
|Windows|<https://github.com/coursier/coursier/releases/download/v@VERSION@/cs-x86_64-pc-win32.exe>|`2.0.0-RC6`|`2.0.16`|
|Any (needs JVM)|<https://github.com/coursier/coursier/raw/v1.1.0-M9/coursier>| |`1.1.0-M9`|

## Command-line options

### Interactive mode

Launched without options, `setup` checks that a JVM and the standard Scala CLI tools are
installed on your system, and updates your profile files (Linux / macOS) or user environment variables
(Windows).

It asks you to confirm prior to performing any of these actions.

To answer yes to all these questions beforehand, pass `--yes` or `-y` to `setup`:
```bash
$ cs setup --yes
```

### Non-interactive mode

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

### Overriding defaults

#### JVM

The JVM installed if [none is found on your system](cli-java.md#system-jvm-detection)
is the same as the [`java` command](cli-java.md), the latest AdoptOpenJDK 8 as of
writing this.

Pass `--jvm` to ignore the already installed jvm and install a custom one:
```bash
$ cs setup --jvm 11
```

#### JVM directory

JVMs are extracted in
[the JVM cache directory](cli-java.md#managed-jvm-directory) by default.

Pass a custom directory to extract JVM under with `--jvm-dir`:
```bash
$ eval "$(cs setup --jvm 11 --jvm-dir test-jvm)"
…
$ echo "$JAVA_HOME"
…/test-jvm/adopt@1.11.0-6
```

#### Applications

The `setup` command installs a number of standard Scala CLI applications by default.
Pass a custom list of applications to install instead with `--apps`.

```bash
$ cs setup --apps sbt-launcher,ammonite
```

`--apps` can be specified multiple times, and expects a `,`-separared list of applications.

See the documentation of the [`install` command](cli-install.md) for more details about
where these applications are defined, how to add your own, etc.

#### Application directory

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

#### Profile files directory

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
- `~/.profile` (created if needed),
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

## Other native Linux launchers

Flavors of the native Linux launchers other than the default one are available. Note that these are only
built for the x86-64 architecture for now (no ARM64).

### Static launchers

These can run in environments where glibc is not available, such as Alpine docker images. These can be
downloaded from GitHub release assets, like the standard launchers. Their file names end with `linux-static`.


```bash
$ curl -fL "https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-linux-static.gz" | gzip -d > cs
```

### Mostly static launchers

These require glibc and libz, but do not need the C++ standard library. They are meant to be used from
so called ["distroless" Docker images](https://github.com/GoogleContainerTools/distroless). These can be
downloaded from GitHub release assets, like the standard launchers. Their file names end with `linux-mostly-static`.


```bash
$ curl -fL "https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-linux-mostly-static.gz" | gzip -d > cs
```

### Container launchers

These are temporary. These are just like the standard launchers, but built without GraalVM container support
(using the `-H:-UseContainerSupport` option at build time). These are meant to workaround issues with
the GraalVM container support, if ever you run into them. These can be
downloaded from GitHub release assets, like the standard launchers. Their file names end with `linux-container`.


```bash
$ curl -fL "https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-linux-container.gz" | gzip -d > cs
```
