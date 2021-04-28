---
title: Examples
---

Examples of common CLI operations.

## Non-interactive setup of a full environment from scratch with custom jvm and apps

```bash
curl -fLo cs https://git.io/coursier-cli-"$(uname | tr LD ld)"
chmod +x cs
./cs setup --yes --jvm graalvm --apps ammonite,bloop,cs,giter8,sbt,scala,scalafmt
rm cs
```

## Updating all the installed apps

```bash
cs update
```

## Launching a REPL with some specific version of Scala and with libraries in the classpath

```bash
cs launch scala --scala 2.13.5 org.typelevel::cats-core:2.6.0
```

You may remove the `--scala` flag if you want to use the default / latest version.
You can also use other REPL like `ammonite` or `scala3-repl`

```bash
cs launch scala3-repl org.typelevel::cats-core:2.6.0
```
