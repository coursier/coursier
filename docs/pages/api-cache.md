# Cache

## Example

```scala mdoc:reset-object:invisible
```

One can download files via the coursier cache with
```scala mdoc:silent
import coursier.cache.FileCache
import coursier.util.Artifact
val cache = FileCache()
val artifact = Artifact("https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.jar")
val file = cache.file(artifact).run.unsafeRun()(cache.ec) // java.io.File
```

## Progress bars

```scala mdoc:reset-object:invisible
```

To get the same progress bars as the coursier CLI, one can do
```scala mdoc:silent
import coursier.cache.FileCache
import coursier.cache.loggers.RefreshLogger
import coursier.util.Artifact
val logger = RefreshLogger.create()
val cache = FileCache().withLogger(logger)
val artifact = Artifact("https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.jar")
val file = logger.using(cache.file(artifact).run).unsafeRun()(cache.ec) // java.io.File
// alternatively
val file0 = logger.use(cache.file(artifact).run.unsafeRun()(cache.ec)) // java.io.File
```

Note the use of `logger.using` (`[T] (=> coursier.util.Task[T]) => coursier.util.Task[T]`)
or `logger.use` (`[T] (=> T) => T`), that properly start and stop the logger before and
after coursier cache use.

## Parallel downloads

```scala mdoc:reset-object:invisible
import coursier.cache.FileCache
```

The coursier cache runs downloads in parallel (up to 6 in parallel by default). It does
so by using a thread pool with at most 6 threads, that it runs downloads from. To pass
an alternative thread pool to the coursier cache, call `withPool` on it, like
```scala mdoc:compile-only
import java.util.concurrent.ExecutorService
val cache = FileCache().withPool(??? : ExecutorService)
```

Get the currently used pool with
```scala mdoc:silent
val cache = FileCache()
cache.pool // ExecutorService
```

## Cache location

```scala mdoc:reset-object:invisible
import coursier.cache.FileCache
```

Get the cache location with
```scala mdoc:silent
val cache = FileCache()
cache.location // java.io.File
```

Override it with
```scala mdoc:compile-only
import java.io.File
val cache = FileCache().withLocation(??? : File)
```

## Other parameters

Numerous aspects of the cache can be adjusted. Feel free to explore its fields and methods via
your IDE or looking at the coursier source code.
