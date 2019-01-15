---
title: scala-native bootstrap
---

The `bootstrap` command of coursier can generate
[Scala Native](https://www.scala-native.org)-based native launchers.

This requires the application you want a launcher for to be cross-compiled to
Scala Native, and its Scala Native artifacts to be published to Maven or Ivy
repositories. The [echo](https://github.com/coursier/echo) project of
coursier has
[such a module](https://github.com/coursier/echo/tree/master/native), currently
published as
[`io.get-coursier:echo_native0.3_2.11:1.0.2`](https://repo1.maven.org/maven2/io/get-coursier/echo_native0.3_2.11/1.0.2).

## Usage

In order to generate a launcher for such a published application, you'll need to
have your environment
[set up for Scala Native](https://www.scala-native.org/en/v0.3.8/user/setup.html#installing-clang-and-runtime-dependencies). You can then generate native
launchers by passing the `--native` or `-S` option to the `bootstrap` command,
like
```bash
$ coursier bootstrap \
    --native \
    io.get-coursier:echo_native0.3_2.11:1.0.2 \
    -o echo
[info] Linking (2354 ms)
[info] Discovered 1291 classes and 9538 methods
…
```
and run them, like
```bash
$ ./echo hey
hey
```

Linking flags during the linking phase can be adjusted via `LDFLAGS` in the
environment.

## Pros and cons

As an application author, thanks to this command, you don't have to generate,
package, then distribute launchers for every platform. Instead, just publish
the Scala Native artifacts to Maven or Ivy repositories, like you would for
a JVM library, and let your users build native executables themselves
via a `coursier boostrap --native` command. Users can generate launchers
adapted to their own environment. A drawback of this approach is that it
requires users to have their machine
[set up for Scala Native](https://www.scala-native.org/en/v0.3.8/user/setup.html#installing-clang-and-runtime-dependencies),
at least when they generate their launcher.

