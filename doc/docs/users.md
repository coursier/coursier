---
title: Users
---

- [scastie](https://github.com/scalacenter/scastie) and [scalafiddle](https://scalafiddle.io) both use coursier to handle dependencies added by users
- the [Ammonite](https://github.com/lihaoyi/Ammonite) shell and the [mill](https://github.com/lihaoyi/mill) build tool both rely on coursier to handle dependencies
- Twitter's internal builds, based on [Pants](https://www.pantsbuild.org), [rely on coursier to handle dependencies](https://www.pantsbuild.org/coursier_migration.html)
- the command-line interfaces of [scalafmt](https://github.com/scalameta/scalafmt) and [scalafix](https://github.com/scalacenter/scalafix) both rely on coursier for their installation
- the scala Jupyter kernels [Toree](https://github.com/apache/incubator-toree) and [almond](https://github.com/jupyter-scala/jupyter-scala) (formerly jupyter-scala) both rely on coursier to handle dependencies
- the Visual Studio Code Scala language servers, [vscode-scala](https://github.com/dragos/dragos-vscode-scala), [metals](https://github.com/scalameta/metals), and [vscode-dotty](https://github.com/lampepfl/dotty/tree/master/vscode-dotty), all rely on coursier to spawn their server process
