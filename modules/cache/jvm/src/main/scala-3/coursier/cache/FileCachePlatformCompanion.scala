package coursier.cache

import coursier.util.Task

/** Scala 3 specific members of the `FileCache` companion.
  *
  * The case class `apply` has default arguments, so no other `apply` overload may have some: the
  * generic form with a default `Sync[F]` (see `create`) cannot be an `apply` here, unlike on Scala
  * 2.
  */
private[cache] trait FileCachePlatformCompanion {
  def apply(): FileCache[Task] =
    FileCache.create[Task]()
}
