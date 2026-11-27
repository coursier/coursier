package coursier.paths

import java.io.File
import java.nio.file.{Path, Paths}
import java.util.Locale

/** Scala Native counterpart of the `coursier.paths.CoursierPaths` Java class of the `paths` module.
  *
  * The JVM implementation gets its base directories from the `directories-jni` library, which is
  * JVM-only. The rules below are the ones `directories` implements for the platforms Scala Native
  * supports (XDG on Linux, `~/Library` on macOS).
  */
object CoursierPaths {

  private lazy val isMac: Boolean =
    Option(System.getProperty("os.name")).map(_.toLowerCase(Locale.ROOT)).exists(_.contains("mac"))

  private def home: Path =
    Paths.get(Option(System.getProperty("user.home")).getOrElse(sys.env.getOrElse("HOME", ".")))

  private def xdg(envName: String, fallback: String): Path =
    Option(System.getenv(envName))
      .filter(_.nonEmpty)
      .map(Paths.get(_))
      .getOrElse(home.resolve(fallback))

  private def projectDir(name: String, macSubDir: String, xdgEnv: String, xdgFallback: String) =
    if (isMac) home.resolve(s"Library/$macSubDir/$name")
    else xdg(xdgEnv, xdgFallback).resolve(name.toLowerCase(Locale.ROOT))

  private def cacheDir(name: String): Path =
    projectDir(name, "Caches", "XDG_CACHE_HOME", ".cache")
  private def configDir(name: String): Path =
    projectDir(name, "Application Support", "XDG_CONFIG_HOME", ".config")
  private def dataLocalDir(name: String): Path =
    projectDir(name, "Application Support", "XDG_DATA_HOME", ".local/share")

  def computeCacheDirectoryFrom(env: String, prop: String, dirName: String): String =
    if (env != null) env
    else if (prop != null) prop
    else {
      val dir = cacheDir("Coursier").resolve(dirName)
      Util.createDirectories(dir)
      dir.toAbsolutePath.normalize().toString
    }

  private def computeCacheDirectory(envVar: String, propName: String, dirName: String): String =
    computeCacheDirectoryFrom(System.getenv(envVar), System.getProperty(propName), dirName)

  def cacheDirectoryPath(): Path =
    Paths.get(computeCacheDirectory("COURSIER_CACHE", "coursier.cache", "v1"))
      .toAbsolutePath.normalize()
  def cacheDirectory(): File = cacheDirectoryPath().toFile

  def archiveCacheDirectoryPath(): Path =
    Paths.get(computeCacheDirectory("COURSIER_ARCHIVE_CACHE", "coursier.archive.cache", "arc"))
      .toAbsolutePath.normalize()
  def archiveCacheDirectory(): File = archiveCacheDirectoryPath().toFile

  def priviledgedArchiveCacheDirectoryPath(): Path =
    Paths.get(computeCacheDirectory(
      "COURSIER_PRIVILEDGED_ARCHIVE_CACHE",
      "coursier.priviledged.archive.cache",
      "priv"
    )).toAbsolutePath.normalize()
  def priviledgedArchiveCacheDirectory(): File = priviledgedArchiveCacheDirectoryPath().toFile

  def digestBasedCacheDirectoryPath(): Path =
    Paths.get(computeCacheDirectory(
      "COURSIER_DIGEST_BASED_CACHE",
      "coursier.digest-based.cache",
      "digest"
    )).toAbsolutePath.normalize()
  def digestBasedCacheDirectory(): File = digestBasedCacheDirectoryPath().toFile

  def jvmCacheDirectoryPath(): Path =
    Paths.get(computeCacheDirectory("COURSIER_JVM_CACHE", "coursier.jvm.cache", "jvm"))
      .toAbsolutePath.normalize()
  def jvmCacheDirectory(): File = jvmCacheDirectoryPath().toFile

  private def computeConfigDirectories(fromEnv: String, fromProps: String): Array[Path] = {
    val path = Option(fromEnv).orElse(Option(fromProps))
    path match {
      case Some(p) => Array(Paths.get(p).toAbsolutePath.normalize())
      case None    => Array(configDir("Coursier").toAbsolutePath.normalize())
    }
  }

  def configDirectoriesPaths(): Array[Path] =
    computeConfigDirectories(
      System.getenv("COURSIER_CONFIG_DIR"),
      System.getProperty("coursier.config-dir")
    )

  def configDirectoriesPaths(fromEnv: String, fromProps: String): Array[Path] =
    if (fromEnv == null && fromProps == null) configDirectoriesPaths()
    else computeConfigDirectories(fromEnv, fromProps)

  def configDirectories(): Array[File] =
    configDirectoriesPaths().map(_.toFile)

  def configDirectories(fromEnv: String, fromProps: String): Array[File] =
    configDirectoriesPaths(fromEnv, fromProps).map(_.toFile)

  def defaultConfigDirectory(): File     = configDirectories().head
  def defaultConfigDirectoryPath(): Path = configDirectoriesPaths().head

  def dataLocalDirectoryPath(): Path = {
    val path = Option(System.getenv("COURSIER_DATA_DIR"))
      .orElse(Option(System.getProperty("coursier.data-dir")))
      .map(Paths.get(_))
      .getOrElse(dataLocalDir("Coursier"))
    path.toAbsolutePath.normalize()
  }
  def dataLocalDirectory(): File = dataLocalDirectoryPath().toFile

  def projectCacheDirectoryPath(): Path = cacheDir("Coursier")
  def projectCacheDirectory(): File     = projectCacheDirectoryPath().toFile

  def scalaConfigFile(fromEnv: String, fromProps: String): Path =
    Option(fromEnv).filter(_.nonEmpty)
      .orElse(Option(fromProps).filter(_.nonEmpty))
      .map(Paths.get(_))
      .getOrElse(dataLocalDir("ScalaCli").resolve("secrets/config.json"))

  def scalaConfigFile(): Path =
    scalaConfigFile(System.getenv("SCALA_CLI_CONFIG"), System.getProperty("scala-cli.config"))

}
