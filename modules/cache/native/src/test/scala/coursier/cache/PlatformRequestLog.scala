package coursier.cache

/** Scala Native counterpart of the JVM `PlatformRequestLog` - there is no http4s here. */
trait PlatformRequestLog { self: RequestLog => }

abstract class PlatformRequestLogCompanion
