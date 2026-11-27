package coursier.cache.internal

private[coursier] object PlatformFileUtil {

  /** Matches the failure a `Files.move(…, ATOMIC_MOVE)` reports when the filesystem cannot do it.
    */
  def atomicMoveNotSupported: PartialFunction[Throwable, Unit] = {
    case _: java.nio.file.AtomicMoveNotSupportedException =>
  }

}
