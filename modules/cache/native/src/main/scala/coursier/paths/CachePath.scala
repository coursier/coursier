package coursier.paths

import java.io.{File, FileNotFoundException}
import java.net.{MalformedURLException, URI, URISyntaxException}
import java.nio.channels.{FileChannel, FileLock, OverlappingFileLockException}
import java.nio.file.{FileSystemException, Files, LinkOption, Path, Paths, StandardOpenOption}
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap

/** Scala Native counterpart of the `coursier.paths.CachePath` Java class of the `paths` module.
  *
  * Kept behaviourally identical to it - see the Java class for the rationale behind the details.
  */
object CachePath {

  private def maxStructureLockAttemptCount =
    Option(System.getProperty("coursier.structure-lock-retry-count")).map(_.toInt).getOrElse(5)
  private def structureLockInitialRetryDelay =
    Option(System.getProperty("coursier.structure-lock-retry-initial-delay-ms")).map(_.toLong)
      .getOrElse(10L)
  private def structureLockRetryDelayMultiplier =
    Option(System.getProperty("coursier.structure-lock-retry-multiplier")).map(_.toDouble)
      .getOrElse(2.0)

  private def throwExceptions =
    java.lang.Boolean.getBoolean("coursier.cache.throw-exceptions")

  // based on https://stackoverflow.com/questions/4571346/how-to-encode-url-to-avoid-special-characters-in-java/4605848#4605848
  // '/' was removed from the unsafe list
  private def escape(input: String): String = {
    val b = new StringBuilder
    for (ch <- input)
      if (isUnsafe(ch)) {
        b.append('%')
        b.append(toHex(ch / 16))
        b.append(toHex(ch % 16))
      }
      else
        b.append(ch)
    b.toString
  }

  private def toHex(ch: Int): Char =
    (if (ch < 10) '0' + ch else 'A' + ch - 10).toChar

  private def isUnsafe(ch: Char): Boolean =
    ch > 128 || " %$&+,:;=?@<>#%".indexOf(ch.toInt) >= 0

  def localFile(
    url: String,
    cache: File,
    user: String,
    localArtifactsShouldBeCached: Boolean
  ): File =
    if (url.startsWith("file:/") && !localArtifactsShouldBeCached)
      try Paths.get(new URI(url)).toFile
      catch {
        case _: URISyntaxException =>
          // Legacy way of converting URL to files in coursier
          if (url.startsWith("file:///"))
            new File(url.substring("file://".length))
          else
            new File(url.substring("file:".length))
      }
    else {
      val split = url.split(":", 2)
      if (split.length != 2)
        throw new MalformedURLException("No protocol found in URL " + url)

      val protocol  = split(0)
      var remaining = split(1)

      if (remaining.startsWith("///"))
        remaining = remaining.substring("///".length)
      else if (remaining.startsWith("/"))
        remaining = remaining.substring("/".length)
      else
        throw new MalformedURLException("URL " + url + " doesn't contain an absolute path")

      if (remaining.endsWith("/"))
        // keeping directory content in .directory files
        remaining = remaining + ".directory"

      while (remaining.startsWith("/"))
        remaining = remaining.substring(1)

      val userPart = if (user == null) "" else user + "@"

      val localPath =
        cache.toPath.normalize().resolve(escape(protocol + "/" + userPart + remaining))
      if (localPath.normalize() != localPath)
        throw new IllegalArgumentException(url + " contains at least one redundant path element")

      localPath.toFile
    }

  def temporaryFile(file: File): File = {
    val dir  = file.getParentFile
    val name = file.getName
    new File(dir, "." + name + ".part")
  }

  def lockFile(path: Path): Path =
    path.getParent.resolve(path.getFileName.toString + ".lock")

  def lockFile(file: File): File =
    new File(file.getParentFile, file.getName + ".lock")

  def defaultCacheDirectory(): File        = CoursierPaths.cacheDirectory()
  def defaultArchiveCacheDirectory(): File = CoursierPaths.archiveCacheDirectory()
  def defaultPriviledgedArchiveCacheDirectory(): File =
    CoursierPaths.priviledgedArchiveCacheDirectory()
  def defaultDigestBasedCacheDirectory(): File = CoursierPaths.digestBasedCacheDirectory()

  // Same trick as the Java implementation: interned strings, so that two copies of this code
  // in the same process lock on the same object.
  private val internedStrings = new ConcurrentHashMap[String, Object]

  private def lockFor(cachePath: Path): Object = {
    val key   = "coursier-jvm-lock-" + cachePath.toString
    val lock0 = internedStrings.get(key)
    if (lock0 == null) {
      val internedKey = key.intern()
      internedStrings.putIfAbsent(internedKey, internedKey)
      internedKey
    }
    else
      lock0
  }

  private final class StructureLockException(parent: Exception) extends Exception(parent)

  private def withStructureLockLoop[V](once: () => V): V = {
    var attemptCount   = 1
    var retryDelay     = structureLockInitialRetryDelay
    var res: Option[V] = None
    while (res.isEmpty && attemptCount < maxStructureLockAttemptCount) {
      attemptCount = attemptCount + 1
      try res = Some(once())
      catch {
        case _: StructureLockException =>
          Thread.sleep(retryDelay)
          retryDelay = (structureLockRetryDelayMultiplier * retryDelay).toLong
      }
    }
    res.getOrElse(once())
  }

  def withStructureLock[V](cache: File, callable: Callable[V]): V =
    withStructureLock(cache.toPath, callable)

  def withStructureLock[V](cache: Path, callable: Callable[V]): V =
    withStructureLockLoop(() => withStructureLockOnce(cache, callable))

  private def withStructureLockOnce[V](cache: Path, callable: Callable[V]): V = {

    val intraProcessLock = lockFor(cache)

    intraProcessLock.synchronized {
      val lockFile = cache.resolve(".structure.lock")
      Util.createDirectories(lockFile.getParent)
      var channel: FileChannel = null

      try {
        /* Deliberately not DELETE_ON_CLOSE, matching the `File` overload of the JVM
         * `CachePath.withStructureLock` - the one the cache actually calls. A close on the failure
         * path would unlink the very file the current holder is locked on, and the next attempt
         * would then create a fresh one and lock that instead. The lock file is removed on the
         * success path below.
         */
        channel =
          try {
            // not following symbolic links, so that a link planted here can't make us create or
            // open another file (Scala Native ignores NOFOLLOW_LINKS, so the deletion is what
            // actually guards against that here)
            Util.deleteIfSymbolicLink(lockFile)
            FileChannel.open(
              lockFile,
              StandardOpenOption.CREATE,
              StandardOpenOption.WRITE,
              LinkOption.NOFOLLOW_LINKS
            )
          }
          catch {
            case ex: FileNotFoundException =>
              throw if (throwExceptions) ex else new StructureLockException(ex)
            case ex: FileSystemException =>
              throw if (throwExceptions) ex else new StructureLockException(ex)
          }

        var lock: FileLock = null
        try {
          lock =
            try channel.lock()
            catch {
              case ex: FileNotFoundException =>
                throw if (throwExceptions) ex else new StructureLockException(ex)
              case ex: OverlappingFileLockException =>
                throw if (throwExceptions) ex else new StructureLockException(ex)
            }

          try callable.call()
          finally {
            lock.release()
            lock = null
            channel.close()
            channel = null
            Files.deleteIfExists(lockFile)
          }
        }
        finally if (lock != null) lock.release()
      }
      finally if (channel != null) channel.close()
    }
  }

}
