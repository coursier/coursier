package coursier.env

import dataclass.data

@data class WindowsEnvVarUpdater(
  powershellRunner: PowershellRunner = PowershellRunner(),
  target: String = "User"
) extends EnvVarUpdater {

  // https://stackoverflow.com/questions/9546324/adding-directory-to-path-environment-variable-in-windows/29109007#29109007
  // https://docs.microsoft.com/fr-fr/dotnet/api/system.environment.getenvironmentvariable?view=netframework-4.8#System_Environment_GetEnvironmentVariable_System_String_System_EnvironmentVariableTarget_
  // https://docs.microsoft.com/fr-fr/dotnet/api/system.environment.setenvironmentvariable?view=netframework-4.8#System_Environment_SetEnvironmentVariable_System_String_System_String_System_EnvironmentVariableTarget_

  private def getEnvironmentVariable(name: String): Option[String] = {
    val output = powershellRunner.runScript(WindowsEnvVarUpdater.getEnvVarScript(name)).stripSuffix(System.lineSeparator())
    if (output == "null") // if ever the actual value is "null", we'll miss it
      None
    else
      Some(output)
  }

  private def setEnvironmentVariable(name: String, value: String): Unit =
    powershellRunner.runScript(WindowsEnvVarUpdater.setEnvVarScript(name, value))

  private def clearEnvironmentVariable(name: String): Unit =
    powershellRunner.runScript(WindowsEnvVarUpdater.clearEnvVarScript(name))

  def applyUpdate(update: EnvironmentUpdate): Boolean = {

    // Beware, these are not an atomic operation overall
    // (we might discard values added by others between our get and our set)

    var setSomething = false

    for ((k, v) <- update.set) {
      val formerValueOpt = getEnvironmentVariable(k)
      val needsUpdate = formerValueOpt.forall(_ != v)
      if (needsUpdate) {
        setEnvironmentVariable(k, v)
        setSomething = true
      }
    }

    for ((k, v) <- update.pathLikeAppends) {
      val formerValueOpt = getEnvironmentVariable(k)
      val alreadyInList = formerValueOpt.exists(_.split(WindowsEnvVarUpdater.windowsPathSeparator).contains(v))
      if (!alreadyInList) {
        val newValue = formerValueOpt.fold(v)(_ + WindowsEnvVarUpdater.windowsPathSeparator + v)
        setEnvironmentVariable(k, newValue)
        setSomething = true
      }
    }

    setSomething
  }

  def tryRevertUpdate(update: EnvironmentUpdate): Boolean = {

    // Beware, these are not an atomic operation overall
    // (we might discard values added by others between our get and our set)

    var setSomething = false

    for ((k, v) <- update.set) {
      val formerValueOpt = getEnvironmentVariable(k)
      val wasUpdated = formerValueOpt.exists(_ == v)
      if (wasUpdated) {
        clearEnvironmentVariable(k)
        setSomething = true
      }
    }

    for ((k, v) <- update.pathLikeAppends; formerValue <- getEnvironmentVariable(k)) {
      val parts = formerValue.split(WindowsEnvVarUpdater.windowsPathSeparator)
      val isInList = parts.contains(v)
      if (isInList) {
        val newValue = parts.filter(_ != v)
        if (newValue.isEmpty)
          clearEnvironmentVariable(k)
        else
          setEnvironmentVariable(k, newValue.mkString(WindowsEnvVarUpdater.windowsPathSeparator))
        setSomething = true
      }
    }

    setSomething
  }

}

object WindowsEnvVarUpdater {

  private def getEnvVarScript(name: String): String =
    s"""[Environment]::GetEnvironmentVariable("$name", "User")
       |""".stripMargin
  private def setEnvVarScript(name: String, value: String): String =
    // FIXME value might need some escaping here
    s"""[Environment]::SetEnvironmentVariable("$name", "$value", "User")
       |""".stripMargin
  private def clearEnvVarScript(name: String): String =
    // FIXME value might need some escaping here
    s"""[Environment]::SetEnvironmentVariable("$name", $$null, "User")
       |""".stripMargin

  private def windowsPathSeparator: String =
    ";"

}
