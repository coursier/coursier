# API

Coursier can be used as a Scala library. Its main dependency is
```
io.get-coursier::coursier:@VERSION@
```

coursier is built mainly for the JVM, although parts of it are also built for
[Scala.js](https://github.com/scala-js/scala-js),
and an even more restrained subset of coursier is also built for
[Scala Native](https://github.com/scala-native/scala-native).

The native CLI of coursier is based on the JVM, and is built using
[GraalVM native image](https://www.graalvm.org/latest/reference-manual/native-image/).
Most if not all coursier features ought to work fine from native images.

The pages that follow document how to use coursier via its API.

Most pages assume that the content of the `coursier` package has been imported,
like
```scala mdoc:silent
import coursier._
```
