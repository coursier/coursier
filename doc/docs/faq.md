---
title: FAQ
---

*Direct former README import (possibly not up-to-date)*

#### Even though the coursier sbt plugin is enabled and some `coursier*` keys can be found from the sbt prompt, dependency resolution seems still to be handled by sbt itself. Why?

Check that the default sbt settings (`sbt.Defaults.defaultSettings`) are not manually added to your project.
These define commands that the coursier sbt plugin overrides. Adding them again erases these overrides,
effectively disabling coursier.

#### With spark >= 1.5, I get some `NoVerifyError` exceptions related to jboss/netty. Why?

This error originates from the `org.jboss.netty:netty:3.2.2.Final` dependency to be put in the classpath.
Exclude it from your spark dependencies with the exclusion `org.jboss.netty:netty`.

Coursier tries to follow the Maven documentation to build the full dependency set, in particular
some [points about dependency exclusion](https://maven.apache.org/guides/introduction/introduction-to-optional-and-excludes-dependencies.html#Dependency_Exclusions).
Inspecting the `org.apache.spark:spark-core_2.11:1.5.2` dependency graph shows that spark-core
depends on `org.jboss.netty:netty:3.2.2.Final` via the following path: `org.apache.spark:spark-core_2.11:1.5.2` ->
`org.tachyonproject:tachyon-client:0.7.1` -> `org.apache.curator:curator-framework:2.4.0` ->
`org.apache.zookeeper:zookeeper:3.4.5` -> `org.jboss.netty:netty:3.2.2.Final`. Even though
spark-core tries to exclude `org.jboss.netty:netty` to land in its classpath via some other dependencies
(e.g. it excludes it via its dependencies towards `org.apache.hadoop:hadoop-client` and `org.apache.curator:curator-recipes`),
it does not via the former path. So it depends on it according to the
[Maven documentation](https://maven.apache.org/guides/introduction/introduction-to-optional-and-excludes-dependencies.html#Dependency_Exclusions).

This is likely unintended, as it leads to exceptions like
```
java.lang.VerifyError: (class: org/jboss/netty/channel/socket/nio/NioWorkerPool, method: createWorker signature: (Ljava/util/concurrent/Executor;)Lorg/jboss/netty/channel/socket/nio/AbstractNioWorker;) Wrong return type in function
```
Excluding `org.jboss.netty:netty` from the spark dependencies fixes it.

#### On first launch, the coursier launcher downloads a 1.5+ MB JAR. Is it possible to have a standalone launcher, that would not need to download things on first launch?

Run `scripts/generate-launcher.sh -s` from the root of the coursier sources. That will generate a new (bigger) `coursier` launcher, that needs not to download anything on first launch.

#### How can the launcher be run on Windows, or manually with the `java` program?

Download it from the same link as the command above. Then run from a console, in the directory where the `coursier` launcher is,
```
> java -noverify -jar coursier
```
The `-noverify` option seems to be required after the proguarding step of the main JAR of coursier.

#### How to enable sandboxing?

Set the `COURSIER_CACHE` prior to running `coursier` or sbt, like
```
$ COURSIER_CACHE=$(pwd)/.coursier-cache coursier
```
or
```
$ COURSIER_CACHE=$(pwd)/.coursier-cache sbt
```
