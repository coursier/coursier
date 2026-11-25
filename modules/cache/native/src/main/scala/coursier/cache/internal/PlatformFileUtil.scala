package coursier.cache.internal

private[coursier] object PlatformFileUtil {

  /** Scala Native's javalib has no `AtomicMoveNotSupportedException`, and its `Files.move` reports
    * a filesystem that cannot move atomically as a plain `IOException`. Falling back to a
    * non-atomic move on any of those is fine - if the move is genuinely impossible, the fallback
    * fails too, and that failure propagates.
    */
  def atomicMoveNotSupported: PartialFunction[Throwable, Unit] = {
    case _: java.io.IOException =>
  }

}
