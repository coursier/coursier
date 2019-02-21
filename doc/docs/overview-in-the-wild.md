---
title: Users
---

One can spot plenty of Scala-related projects using coursier in the wild. Here are a few of them.

## scastie and scalafiddle

[scastie](https://github.com/scalacenter/scastie) and
[scalafiddle](https://scalafiddle.io) both use coursier to handle dependencies
added by users.

## Ammonite and mill

The [Ammonite](https://github.com/lihaoyi/Ammonite) shell and the
[mill](https://github.com/lihaoyi/mill) build tool both rely on coursier to
handle dependencies.

## Pants

Twitter's internal builds, based on [Pants](https://www.pantsbuild.org),
[rely on coursier to handle dependencies](https://www.pantsbuild.org/coursier_migration.html).

## scalafmt and scalafix

The command-line interfaces of [scalafmt](https://github.com/scalameta/scalafmt)
and [scalafix](https://github.com/scalacenter/scalafix) both rely on coursier
for their installation.

## Scala jupyter kernels

The scala Jupyter kernels [Toree](https://github.com/apache/incubator-toree)
and [almond](https://github.com/jupyter-scala/jupyter-scala) (formerly
jupyter-scala) both rely on coursier to handle dependencies.

## Scala vscode language servers

The Visual Studio Code Scala language servers,
[metals](https://github.com/scalameta/metals),
[vscode-scala](https://github.com/dragos/dragos-vscode-scala), and
[vscode-dotty](https://github.com/lampepfl/dotty/tree/master/vscode-dotty), all
rely on coursier to spawn their server process.

## Other build tools

[bazel-deps](https://github.com/johnynek/bazel-deps) allows to handle Maven
dependencies in bazel using coursier. The [fury](https://fury.build) build
tool handles Maven dependencies via coursier.
