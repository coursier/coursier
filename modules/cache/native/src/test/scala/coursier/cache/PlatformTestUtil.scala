package coursier.cache

/** Scala Native counterpart of the JVM `PlatformTestUtil`.
  *
  * The helpers it holds on the JVM are all http4s-, TLS- or classpath-resource-based, none of which
  * exists here - Native tests use [[RawHttpServer]] instead.
  */
abstract class PlatformTestUtil
