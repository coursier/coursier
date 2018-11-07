# Cookbook of stuff to do while developing on coursier

The sources of coursier rely on some git submodules. Clone the sources of coursier via
```
$ git clone --recursive https://github.com/coursier/coursier.git
```
or run
```
$ git submodule update --init --recursive
```
from the coursier sources to initialize them.

The latter command also needs to be run whenever these submodules are updated.

## Compile and run the CLI

```
$ sbt cli/pack
…
$ modules/cli/target/pack/bin/coursier --help
…
```

## Automatically re-compile the CLI

`sbt "~cli/pack"` watches the sources, and re-generates `modules/cli/target/pack` accordingly.

## Run unit tests (JVM)

```
$ sbt
> testsJVM/testOnly coursier.util.TreeTests
> testsJVM/test
```

`testOnly` runs the tests that match the expression it is passed.
`test` runs all the tests.

To run the tests each time the sources change, prefix the test commands with
`~`, like
```
$ sbt
> ~testsJVM/testOnly coursier.util.TreeTests
> ~testsJVM/test
```

## Run unit tests (JS)

The JS tests require node to be installed. They automatically run `npm install` from the root of the coursier sources if needed.

JS tests can be run like JVM tests, like
```
$ sbt
> testsJS/testOnly coursier.util.TreeTests
> testsJS/test
```

Like for the JVM tests, prefix test commands with `~` to watch sources (see above).

## Run integration tests

### Main tests

Run the main integration tests with
```
$ sbt testsJVM/it:test
```

### Nexus proxy tests

Run the proxy integration tests with
```
$ sbt proxy-tests/it:test
```

Note that these tests automatically spawn, then stop, docker containers running Sonatype Nexus servers.

### Build with Pants

[Pants](https://github.com/pantsbuild/pants) build tool is also added to an experimental path to build the software

Currently only the CLI command can be built via Pants with Scala 2.12.4.

To iterate on code changes:

```
cd modules
./pants run cli/src/main/scala-2.12:coursier-cli -- fetch --help
```

To build a distributable binary
```
cd modules
./pants binary cli/src/main/scala-2.12:coursier-cli

# Artifact will be placed under dist/
java -jar dist/coursier-cli.jar fetch --help
```

## Build the web demo

coursier is cross-compiled to scala-js, and can run in the browser. It has a [demo web site](https://coursier.github.io/coursier/#demo), that runs resolutions straight from your web browser.

Its sources are in the `web` module.

To build and test this demo site locally, you can do
```
$ sbt web/fastOptJS
$ open modules/web/target/scala-2.12/classes/index.html
```
(on Linux, use `xdg-open` instead of `open`)


# Merging PRs on GitHub

Use either "Create merge commit" or "Squash and merge".

Use "Create merge commit" if the commit list is clean enough (each commit has a clear message, and doesn't break simple compilation and test tasks).

Use "Squash and merge" in the other cases.

# General Versioning Guideline

* Major Version 1.x.x : Increment this field when there is a major change.
* Minor Version x.1.x : Increment this field when there is a minor change that breaks backward compatibility for an method.
* Patch version x.x.1 : Increment this field when a minor format change that just adds information that an application can safely ignore.

# Deprecation Strategy

When deprecating a method/field, we want to know
1. Since which version this field/method is being deprecated
2. Migration path, i.e. what to use instead
3. At which point the deprecation will be removed

Due to scala's builtin deprecation works like
```
class deprecated(message: String = {}, since: String = {})
```
we need to put 2) and 3) into `message`:
```
@deprecated(message = "<migration path>. <version to be removed>", since: "deprecation start version")
```

Typically there needs to be at least 2 minor versions between since-version and to-be-removed-version to help migration.

For example, if since version is 1.1.0, then deprecation can be removed in 1.3.0
