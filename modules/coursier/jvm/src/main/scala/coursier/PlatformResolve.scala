package coursier

import scala.cli.config.{ConfigDb, Keys}

import coursier.cache.CacheEnv
import coursier.core.Repository
import coursier.params.{Mirror, MirrorConfFile}
import coursier.paths.CoursierPaths
import coursier.proxy.SetupProxy

abstract class PlatformResolve {

  type Path = java.nio.file.Path

  /** Default locations for the Scala config file */
  lazy val defaultConfFiles: Seq[Path] =
    CacheEnv.defaultConfFiles(CoursierEnv.scalaCliConfig.read())

  /** Default locations for the mirror repositories config file */
  lazy val defaultMirrorConfFiles: Seq[MirrorConfFile] =
    CoursierEnv.defaultMirrorConfFiles(
      CoursierEnv.mirrors.read(),
      CoursierEnv.mirrorsExtra.read(),
      CacheEnv.configDir.read()
    )

  /** Default location of the Maven settings file
    *
    * Empty if the reading of Maven settings is disabled, see `CoursierEnv.mavenSettings`.
    */
  lazy val defaultMavenSettingsFile: Option[Path] =
    CoursierEnv.defaultMavenSettingsFile(
      CoursierEnv.mavenSettings.read(),
      CoursierEnv.mavenHome.read(),
      CoursierEnv.mavenHomeFallback.read()
    )

  /** Default value for mirror repositories
    *
    * Mirrors from the coursier configuration come first, so that they take precedence over the ones
    * read from the Maven settings file.
    */
  lazy val defaultMirrors: Seq[Mirror] =
    CoursierEnv.defaultMirrors(
      CoursierEnv.mirrors.read(),
      CoursierEnv.mirrorsExtra.read(),
      CoursierEnv.scalaCliConfig.read(),
      CacheEnv.configDir.read()
    ) ++
      CoursierEnv.defaultMavenSettingsMirrors(
        CoursierEnv.mavenSettings.read(),
        CoursierEnv.mavenHome.read(),
        CoursierEnv.mavenHomeFallback.read()
      )

  def confFileMirrors(confFile: Path): Seq[Mirror] =
    CoursierEnv.confFileMirrors(confFile)

  /** Mirrors read from the `mirrors` section of a Maven settings file */
  def mavenSettingsMirrors(settingsFile: Path): Seq[Mirror] =
    CoursierEnv.mavenSettingsMirrors(settingsFile)

  def confFileRepositories(confFile: Path): Option[Seq[Repository]] =
    CoursierEnv.confFileRepositories(confFile)

  /** Default value for repositories */
  lazy val defaultRepositories: Seq[Repository] =
    CoursierEnv.defaultRepositories(
      CoursierEnv.repositories.read(),
      CoursierEnv.scalaCliConfig.read()
    )

  def proxySetup(): Unit =
    if (!SetupProxy.setup()) {
      val configPath = CoursierPaths.scalaConfigFile()
      val db         = ConfigDb.open(configPath)
        .fold(e => throw new Exception(e), identity)
      val addrOpt     = db.get(Keys.proxyAddress).fold(e => throw new Exception(e), identity)
      val userOpt     = db.get(Keys.proxyUser).fold(e => throw new Exception(e), identity)
      val passwordOpt = db.get(Keys.proxyPassword).fold(e => throw new Exception(e), identity)

      for (addr <- addrOpt) {
        val userOrNull     = userOpt.map(_.get().value).orNull
        val passwordOrNull = passwordOpt.map(_.get().value).orNull
        SetupProxy.setProxyProperties(addr, userOrNull, passwordOrNull, "")
        SetupProxy.setupAuthenticator()
      }
    }

}
