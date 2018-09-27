---
title: Users
---

*Direct former README import (possibly not up-to-date)*

- [Lars Hupel](https://github.com/larsrh/)'s [libisabelle](https://github.com/larsrh/libisabelle) fetches
some of its requirements via coursier,
- [jupyter-scala](https://github.com/alexarchambault/jupyter-scala) is launched
and allows to add dependencies in its sessions with coursier (initial motivation
for writing coursier),
- [Apache Toree](https://github.com/apache/incubator-toree) - formerly known as [spark-kernel](https://github.com/ibm-et/spark-kernel), is now using coursier to
add dependencies on-the-fly ([#4](https://github.com/apache/incubator-toree/pull/4)),
- [Quill](https://github.com/getquill/quill) is using coursier for faster dependency resolution ([#591](https://github.com/getquill/quill/pull/591)),
- [vscode-scala](https://github.com/dragos/dragos-vscode-scala) uses coursier to fetch and launch its ensime-based server,
- [Ammonite](https://github.com/lihaoyi/Ammonite) uses coursier to fetch user-added dependencies since version `0.9.0`,
- [ensime-sbt](https://github.com/ensime/ensime-sbt) uses coursier to get the ensime server classpath,
- [scalafmt](https://github.com/scalameta/scalafmt) relies on coursier for its CLI installation,
- [scalafiddle](https://scalafiddle.io) uses coursier to fetch user-added dependencies,
- Your project here :-)
