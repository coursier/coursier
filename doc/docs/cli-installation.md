---
title: Installation
---

These instructions will install the coursier CLI `cs` itself, as well as a typical Scala development environment.
By default, they will install the following applications:

- `cs` itself, to further manage your Scala environment
- `scala`, the Scala 2 REPL
- `scalac`, the Scala 2 compiler
- `sbt` and `sbtn`, the [sbt build toold](https://www.scala-sbt.org/)
- `ammonite`, [an enhanced REPL](https://ammonite.io/) for Scala 2
- `scalafmt`, the [Scala code formatter](https://scalameta.org/scalafmt/)

They will also install a JVM if none is found on the system.

If you want more control over what gets installed and how, read about the [`setup`](cli-setup.md) command.

After the setup, you can [start using Scala](https://docs.scala-lang.org/scala3/getting-started.html#create-a-hello-world-project-with-sbt), or install more applications with the [`install`](cli-install.md) command.

## Native launcher

### Linux

On Linux, download and run the coursier installer with

```bash
$ curl -fL https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-linux.gz | gzip -d > cs
$ chmod +x cs
$ ./cs setup
```

### macOS

On macOS, download and run the coursier installer with

```bash
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

On Windows, [download and execute the Windows installer](https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-win32.zip).

If you prefer a command line-based install, or if you would like to customize the setup options, use:

```pwsh
# PowerShell
Invoke-WebRequest -Uri "https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-win32.zip" -OutFile "cs-x86_64-pc-win32.zip"
Expand-Archive -Path "cs-x86_64-pc-win32.zip"
Rename-Item -Path "cs-x86_64-pc-win32.exe" -NewName "cs.exe"
Remove-Item -Path "cs-x86_64-pc-win32.zip"
.\cs --help
```

```bat
:: CMD
curl -fLo cs-x86_64-pc-win32.zip https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-win32.zip
tar -xf cs-x86_64-pc-win32.zip
move cs-x86_64-pc-win32.exe cs.exe
.\cs --help
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
cs --completions zsh > ~/.zsh/completion/cs
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
