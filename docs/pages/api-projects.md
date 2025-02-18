# coursier sub-projects

Some coursier core APIs have been moved to external repositories, so that these
can be used in a standalone fashion.

## coursier-versions

The [coursier-versions](https://github.com/coursier/versions) project publishes one
main artifact, `io.get-coursier::versions`. It defines classes under `coursier.version`,
most notably

* `Version`
* `VersionInterval`
* `VersionConstraint`

These are now used by the core module of coursier and by its public API.
Their use should be preferred to their deprecated equivalents under `coursier.core`.

## coursier-dependency

The [coursier-dependency](https://github.com/coursier/dependency) project allows to define
and parse dependencies. It can do so on the JVM, Scala.js, and Scala Native too (the latter
now being supported by coursier itself). It mainly publishes the `io.get-coursier::dependency`
module. It defines classes under the `dependency` package (right under root, so `_root_.dependency`).

It's only used internally by coursier for now, but isn't part of its public API yet.

The main user of this project for now should be [Scala CLI](https://github.com/VirtusLab/scala-cli),
that uses it to parse dependencies in `//> using dep` directives, among others.
