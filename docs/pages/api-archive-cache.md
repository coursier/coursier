# Archive cache

## Example

```scala mdoc:reset-object:invisible
```

Get the directory where an archive is extracted with
```scala mdoc:silent
import coursier.cache.ArchiveCache
import coursier.util.Artifact
val archiveCache = ArchiveCache()
val dir = archiveCache.get(Artifact("https://cosmo.zip/pub/cosmocc/cosmocc-3.9.7.zip")).unsafeRun()(archiveCache.cache.ec) // java.io.File
```

## Progress bars

```scala mdoc:reset-object:invisible
```

[Enable those on a standard cache](api-cache.md#progress-bars), and pass it to the `ArchiveCache` to get progress bars
when files are being downloaded:
```scala mdoc:silent
import coursier.cache.{ArchiveCache, FileCache}
import coursier.cache.loggers.RefreshLogger
import coursier.util.Artifact
val logger = RefreshLogger.create()
val cache = FileCache().withLogger(logger)
val archiveCache = ArchiveCache().withCache(cache)
val dir = logger.using(archiveCache.get(Artifact("https://cosmo.zip/pub/cosmocc/cosmocc-3.9.7.zip"))).unsafeRun()(archiveCache.cache.ec) // java.io.File
```

Note that there's no progress reporting while the archive is being extracted for now.

## Archive cache location

```scala mdoc:reset-object:invisible
import coursier.cache.ArchiveCache
```

Get the archive cache location with
```scala mdoc:silent
val archiveCache = ArchiveCache()
cache.location // java.io.File
```

Set the directory under which all archives are extracted with

Override it with
```scala mdoc:compile-only
import java.io.File
val archiveCache = ArchiveCache().withLocation(??? : File)
```

Note that this only changes the directory under which archives are *extracted*, not
the directory where these are *downloaded*. To change the latter too,
[create a standard cache instance with a custom location](api-cache.md#cache-location),
and pass it to the `ArchiveCache` with `withCache` like above.
