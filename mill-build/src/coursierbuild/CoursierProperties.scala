package coursierbuild

object CoursierProperties {

  /** Coursier system properties, that shouldn't be shaded
    *
    * JarJar remaps string constants that look like class names, so that these would be renamed to
    * `coursierapi.shaded.coursier.…` without specific rules, see
    * [[https://github.com/coursier/interface/issues/477]].
    */
  def list = Seq(
    "coursier.archive.cache",
    "coursier.cache",
    "coursier.cache.server",
    "coursier.cache.server.password",
    "coursier.cache.server.user",
    "coursier.cache.throw-exceptions",
    "coursier.config-dir",
    "coursier.connect-timeout",
    "coursier.core.throw-exceptions",
    "coursier.credentials",
    "coursier.data-dir",
    "coursier.digest-based.cache",
    "coursier.directories.powershell-debug",
    "coursier.exception-retry",
    "coursier.exception-retry-backoff-initial-delay",
    "coursier.exception-retry-backoff-max-delay",
    "coursier.exception-retry-backoff-multiplier",
    "coursier.http.maxRedirects",
    "coursier.ivy.home",
    "coursier.jni",
    "coursier.jni.check.throw",
    "coursier.jvm.cache",
    "coursier.max-http-retry-after",
    "coursier.mirrors",
    "coursier.mirrors.extra",
    "coursier.mode",
    "coursier.parallel-download-count",
    "coursier.priviledged.archive.cache",
    "coursier.read-timeout",
    "coursier.repositories",
    "coursier.retry-poll-max-delay",
    "coursier.sslexception-retry",
    "coursier.structure-lock-retry-count",
    "coursier.structure-lock-retry-initial-delay-ms",
    "coursier.structure-lock-retry-multiplier",
    "coursier.ttl",
    "coursier.windows.disable-ffm"
  )

}
