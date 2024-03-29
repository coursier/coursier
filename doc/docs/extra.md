---
title: Extra
---

*Direct former README import (possibly not up-to-date)*

## Printing trees

E.g. to print the dependency tree of `io.circe:circe-core:0.4.1`,
```
$ cs resolve -t io.circe:circe-core_2.11:0.4.1
  Result:
└─ io.circe:circe-core_2.11:0.4.1
   ├─ io.circe:circe-numbers_2.11:0.4.1
   |  └─ org.scala-lang:scala-library:2.11.8
   ├─ org.scala-lang:scala-library:2.11.8
   └─ org.typelevel:cats-core_2.11:0.4.1
      ├─ com.github.mpilquist:simulacrum_2.11:0.7.0
      |  ├─ org.scala-lang:scala-library:2.11.7 -> 2.11.8
      |  └─ org.typelevel:macro-compat_2.11:1.1.0
      |     └─ org.scala-lang:scala-library:2.11.7 -> 2.11.8
...
```

From sbt, with sbt-coursier enabled, the command `coursierDependencyTree` prints the dependency tree of the various sub-projects,
```
> coursierDependencyTree
io.get-coursier:coursier_2.11:1.0.1-SNAPSHOT
├─ com.lihaoyi:fastparse_2.11:0.3.7
|  ├─ com.lihaoyi:fastparse-utils_2.11:0.3.7
|  |  ├─ com.lihaoyi:sourcecode_2.11:0.1.1
|  |  |  └─ org.scala-lang:scala-library:2.11.7 -> 2.11.8
|  |  └─ org.scala-lang:scala-library:2.11.7 -> 2.11.8
|  ├─ com.lihaoyi:sourcecode_2.11:0.1.1
|  |  └─ org.scala-lang:scala-library:2.11.7 -> 2.11.8
|  └─ org.scala-lang:scala-library:2.11.7 -> 2.11.8
├─ org.jsoup:jsoup:1.9.2
...
```

Note that this command can be scoped to sub-projects, like `proj/coursierDependencyTree`.

The printed trees highlight version bumps, that only change the patch number, in yellow. The `2.11.7 -> 2.11.8` above mean that the parent dependency wanted version `2.11.7`, but version `2.11.8` landed in the classpath, pulled in this version by other dependencies.

They highlight in red version bumps that may not be binary compatible, changing major or minor version number.

## Generating bootstrap launchers

The `coursier bootstrap` command generates tiny bootstrap launchers (~30 kB). These are able to download their dependencies upon first launch, then launch the corresponding application. E.g. to generate a launcher for scalafmt,
```
$ cs bootstrap com.geirsson:scalafmt-cli_2.11:0.2.3 -o scalafmt
```

This generates a `scalafmt` file, which is a tiny JAR, corresponding to the `bootstrap` sub-project of coursier. It contains resource files, with the URLs of the various dependencies of scalafmt. On first launch, these are downloaded under `~/.coursier/bootstrap/com.geirsson/scalafmt-cli_2.11` (following the organization and name of the first dependency - note that this directory can be changed with the `-D` option). Nothing needs to be downloaded once all the dependencies are there, and the application is then launched straightaway.

## Credentials

To use artifacts from repositories requiring credentials, pass the user and password via the repository URL, like
```
$ cs fetch -r https://user:pass@company.com/repo com.company:lib:0.1.0
```

From sbt, add the setting `coursierUseSbtCredentials := true` for sbt-coursier to use the credentials set via the `credentials` key. This manual step was added in order for the `credentials` setting not to be checked if not needed, as it seems to acquire some (good ol') global lock when checked, which sbt-coursier aims at avoiding.

## Extra protocols

By default, coursier and sbt-coursier handle the following protocols:

* `http://`
* `https://`
* `file://`
* protocols supported by `java.net.URL` (not thoroughly tested). See [javadoc](https://docs.oracle.com/en/java/javase/15/docs/api/java.base/java/net/URL.html#%3Cinit%3E(java.lang.String,java.lang.String,int,java.lang.String)) for more details.

Other protocols can be added by implementing `java.net.URLStreamHandlerFactory` under the `coursier.cache.protocol` package name:

```scala
package coursier.cache.protocol

import java.io.File
import java.io.InputStream
import java.net.{URL, URLConnection, URLStreamHandler, URLStreamHandlerFactory}
import java.io.FileInputStream

class CustomprotocolHandler extends URLStreamHandlerFactory {
  def createURLStreamHandler(protocol: String): URLStreamHandler = new URLStreamHandler {
    def openConnection(url: URL): URLConnection =
      new URLConnection(url) {
        def connect(): Unit = ()
        override def getInputStream(): InputStream =
          new FileInputStream(new File(new File(".").getAbsolutePath() + url.getPath()))
      }
  }
}
```

Coursier will search for your plugin with the following order on various classloaders:

1. Custom classloaders provided via the API (see `FileCache.withClassLoaders`)
2. `Thread.currentThread().getContextClassLoader`
3. The classloader that loaded coursier itself (more precisely, `coursier.cache.CacheUrl.getClass.getClassLoader`)


Real Word Examples:

* [coursier-s3](https://github.com/alexarchambault/coursier-s3), a plugin for S3, is under development
