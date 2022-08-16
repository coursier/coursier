## Requirements

Before anything, we need the code from other repositories. Use `git submodule` to get it:

```bash
git submodule update --init --recursive
```

## Mill commands cookbook

Coursier is built with [Mill](https://com-lihaoyi.github.io/mill).

The Mill build is defined in `build.sc`, and `.sc` scripts under `project/`.

The `./mill` script runs Mill, and can be used to run Mill tasks and commands.

We list below commands that are useful when developing on coursier. Note that
most commands also accept a `-w` option (to be passed right after `./mill`, like
`./mill -w …`), to watch the sources for changes, and re-run a task or command upon change.
The build sources (`build.sc`, scripts under `project/`) are also watched
for changes.

These commands sometimes rely on the `-i` option of mill. This option runs tasks
or commands in the current terminal, rather than in a Mill server running in the
background. Some commands or tasks using the terminal (querying its size, …)
require this option to run well.

### Compile everything

```text
$ ./mill __.compile
```

While watching sources:
```text
$ ./mill -w __.compile
```

### IDE

#### Metals

[Metals](https://scalameta.org/metals) is the recommended IDE to develop on coursier.

Using the default import functionality of Metals is _not_ recommended. Instead,
generate bloop configuration files with
```text
$ ./mill mill.contrib.bloop.Bloop/install
```

Then run the "Metals: Connect to build server" command.

Repeat these steps to re-import the project if you change the build configuration.

#### IntelliJ (not recommended)

Coursier relies on macro annotations (via [data-class](https://github.com/alexarchambault/data-class)),
which are not supported by IntelliJ. As a consequence, editing the coursier sources in IntelliJ shows
many spurious errors. It is recommended to use Metals rather than IntelliJ to develop
on coursier.

If you still want to open the coursier sources in IntelliJ, generate IntelliJ configuration files with
the following command rather than using the import project functionality of IntelliJ:
```text
$ ./mill mill.scalalib.GenIdea/idea
```

IntelliJ should automatically pick up changes in the generated config files.

Run this command again to re-import the project if you change the build configuration.

### Run the CLI from sources

```text
$ ./mill -i cli.run …args…
```

### Generate a JVM launcher of the CLI

```text
$ ./mill show cli.standaloneLauncher
```

This should print the path to the generated launcher, that can be copied, or run on other
machines.

### Generate a GraalVM native image of the CLI

```text
$ ./mill -i show cli.nativeImage
```

This should print the path to the generated native image.

### Generate a GraalVM native image of the CLI and test it

```text
$ ./mill nativeTests
```

Use the command in the previous section to print the path to the generated native image.

### Run all Scala.JS tests

```text
$ ./mill jsTests
```

### Run all Scala.JS tests for a specific Scala version

```text
$ ./mill jsTests 2.13.3
```

### Run all JVM-based tests

```text
$ ./mill jvmTests
```

### Run all JVM-based tests for a specific Scala version

```text
$ ./mill jvmTests 2.13.3
```

### Validate the documentation markdown files

```text
$ ./mill -i doc.generate
```

### Validate the documentation markdown files and generate a static version of the website

```text
$ ./mill -i doc.generate --npm-install --yarn-run-build
$ npx http-server doc/website/build/coursier
```

In watch mode (only watches markdown files):
```text
$ ./mill -i doc.generate --npm-install --yarn-run-build --watch
```

### Check binary compatibility

```text
$ ./mill __.mimaReportBinaryIssues
```
