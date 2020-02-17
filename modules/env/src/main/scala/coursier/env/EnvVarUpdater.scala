package coursier.env

import java.util.Locale

abstract class EnvVarUpdater {
  def applyUpdate(update: EnvironmentUpdate): Boolean
  def tryRevertUpdate(update: EnvironmentUpdate): Boolean
}

object EnvVarUpdater {
  def osSpecificUpdater(os: String): EnvVarUpdater = {
    val isWindows = os.toLowerCase(Locale.ROOT).contains("windows")
    if (isWindows)
      WindowsEnvVarUpdater()
    else
      ProfileUpdater()
  }

  def osSpecificUpdater(): EnvVarUpdater =
    osSpecificUpdater(Option(System.getProperty("os.name")).getOrElse(""))
}
