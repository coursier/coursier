package coursier.cache

import coursier.util.{Sync, Task}

/** Scala 2 specific members of the `FileCache` companion.
  *
  * The generic `apply` below has a default argument, which is only allowed here because data-class
  * generates the other `apply` overloads without any (unlike the Scala 3 compiler, so Scala 3 gets
  * a non-generic `apply()` instead).
  */
private[cache] trait FileCachePlatformCompanion {
  def apply[F[_]]()(implicit S: Sync[F] = Task.sync): FileCache[F] =
    FileCache.create[F]()(S)
}
