---
title: Installation
---

## Native launcher

### Linux

On Linux, download and run the coursier launcher with
```bash
$ curl -fLo cs https://git.io/coursier-cli-linux &&
    chmod +x cs &&
    ./cs
```

### macOS

Download and run the coursier launcher with
```bash
$ curl -fLo cs https://git.io/coursier-cli-macos &&
    chmod +x cs &&
    (xattr -d com.apple.quarantine cs || true) &&
    ./cs
```

Note the `xattr` command to circumvent notarization on macOS Catalina.

Alternatively, the native launcher can be installed via [homebrew](https://brew.sh) with
```bash
$ brew install coursier/formulas/coursier
$ cs
```

### Windows

On Windows, use
```bash
> bitsadmin /transfer cs-cli https://git.io/coursier-cli-windows-exe "%cd%\cs.exe"
> .\cs --help
```
Note that this must be run with `cmd.exe`, not `PowerShell`.

## JAR-based launcher

In case you run into any issue with the [native launcher](#native-launcher),
a JAR-based launcher is available.

> The commands below install the JAR-based launcher as `coursier`, which can be
substituted to `cs` in the various examples on this website.

### Linux / macOS

Download and run the launcher with
```bash
$ curl -fLo coursier https://git.io/coursier-cli &&
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
