package coursier.env

import dataclass._

@data class WindowsEnvVarUpdater(
  powershellRunner: PowershellRunner = PowershellRunner(),
  target: String = "User",
  @since
  useJni: Option[Boolean] = None
) extends EnvVarUpdater {

  private lazy val useJni0 = useJni.getOrElse {
    // FIXME Should be coursier.paths.Util.useJni(), but it's not available from here.
    !System.getProperty("coursier.jni", "").equalsIgnoreCase("false")
  }

  // https://stackoverflow.com/questions/9546324/adding-directory-to-path-environment-variable-in-windows/29109007#29109007
  // https://docs.microsoft.com/fr-fr/dotnet/api/system.environment.getenvironmentvariable?view=netframework-4.8#System_Environment_GetEnvironmentVariable_System_String_System_EnvironmentVariableTarget_
  // https://docs.microsoft.com/fr-fr/dotnet/api/system.environment.setenvironmentvariable?view=netframework-4.8#System_Environment_SetEnvironmentVariable_System_String_System_String_System_EnvironmentVariableTarget_

  private def getEnvironmentVariable(name: String): Option[String] =
    if (useJni0)
      Option(coursier.jniutils.WindowsEnvironmentVariables.get(name))
    else {
      val output = powershellRunner
        .runScript(WindowsEnvVarUpdater.getEnvVarScript(name))
        .stripSuffix(System.lineSeparator())
      if (output == "null") // if ever the actual value is "null", we'll miss it
        None
      else
        Some(output)
    }

  private def setEnvironmentVariable(name: String, value: String): Unit =
    if (useJni0)
      coursier.jniutils.WindowsEnvironmentVariables.set(name, value)
    else
      powershellRunner.runScript(WindowsEnvVarUpdater.setEnvVarScript(name, value))

  private def clearEnvironmentVariable(name: String): Unit =
    if (useJni0)
      coursier.jniutils.WindowsEnvironmentVariables.delete(name)
    else
      powershellRunner.runScript(WindowsEnvVarUpdater.clearEnvVarScript(name))

  def applyUpdate(update: EnvironmentUpdate): Boolean = {

    // Beware, these are not an atomic operation overall
    // (we might discard values added by others between our get and our set)

    var setSomething = false

    val newJavaHomeOpt = update.set.find(_._1 == "JAVA_HOME").map(_._2)

    // Track the old JAVA_HOME before we update it, for PATH cleanup below
    val oldJavaHomeOpt =
      if (update.set.exists(_._1 == "JAVA_HOME")) getEnvironmentVariable("JAVA_HOME")
      else None

    for ((k, v) <- update.set) {
      val formerValueOpt = getEnvironmentVariable(k)
      val needsUpdate    = formerValueOpt.forall(_ != v)
      if (needsUpdate) {
        setEnvironmentVariable(k, v)
        setSomething = true
      }
    }

    for ((k, v) <- update.pathLikeAppends) {
      // Detect a JVM bin directory update: the path being added is {new JAVA_HOME}\bin
      val isJvmBinUpdate = k == "PATH" && newJavaHomeOpt.exists { h =>
        v.equalsIgnoreCase(h + "\\bin") || v.equalsIgnoreCase(h + "/bin")
      }

      if (isJvmBinUpdate) {
        // Use %JAVA_HOME%\bin as the PATH entry instead of an absolute path.
        // This way, when JAVA_HOME is updated, PATH automatically resolves to the new JVM.
        val ref            = WindowsEnvVarUpdater.javaHomeBinRef
        val formerValueOpt = getEnvironmentVariable(k)
        val parts = formerValueOpt.fold(Array.empty[String])(
          _.split(WindowsEnvVarUpdater.windowsPathSeparator, -1)
        )
        val hasRef = parts.exists(_.equalsIgnoreCase(ref))

        // Remove the old absolute JVM bin path from PATH if present (backward compatibility
        // with setups that stored the absolute path instead of the %JAVA_HOME%\bin reference)
        val staleEntries: Set[String] =
          Set(v) ++
            oldJavaHomeOpt.map(_ + "\\bin").toSet ++
            oldJavaHomeOpt.map(_ + "/bin").toSet
        val filteredParts = parts.filterNot(p => staleEntries.exists(p.equalsIgnoreCase(_)))

        if (!hasRef || filteredParts.length < parts.length) {
          val newParts = if (hasRef) filteredParts else filteredParts :+ ref
          val newValue = newParts.mkString(WindowsEnvVarUpdater.windowsPathSeparator)
          if (newValue.isEmpty) clearEnvironmentVariable(k)
          else setEnvironmentVariable(k, newValue)
          setSomething = true
        }
      }
      else {
        val formerValueOpt = getEnvironmentVariable(k)
        val alreadyInList = formerValueOpt
          .exists(_.split(WindowsEnvVarUpdater.windowsPathSeparator).contains(v))
        if (!alreadyInList) {
          val newValue = formerValueOpt.fold(v)(_ + WindowsEnvVarUpdater.windowsPathSeparator + v)
          setEnvironmentVariable(k, newValue)
          setSomething = true
        }
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
      val wasUpdated     = formerValueOpt.exists(_ == v)
      if (wasUpdated) {
        clearEnvironmentVariable(k)
        setSomething = true
      }
    }

    val newJavaHomeOpt = update.set.find(_._1 == "JAVA_HOME").map(_._2)
    for ((k, v) <- update.pathLikeAppends; formerValue <- getEnvironmentVariable(k)) {
      val isJvmBinUpdate = k == "PATH" && newJavaHomeOpt.exists { h =>
        v.equalsIgnoreCase(h + "\\bin") || v.equalsIgnoreCase(h + "/bin")
      }

      val toRemove: Set[String] =
        if (isJvmBinUpdate) Set(v, WindowsEnvVarUpdater.javaHomeBinRef)
        else Set(v)

      val parts   = formerValue.split(WindowsEnvVarUpdater.windowsPathSeparator)
      val matched = parts.exists(p => toRemove.exists(p.equalsIgnoreCase(_)))
      if (matched) {
        val newParts = parts.filterNot(p => toRemove.exists(p.equalsIgnoreCase(_)))
        if (newParts.isEmpty)
          clearEnvironmentVariable(k)
        else
          setEnvironmentVariable(k, newParts.mkString(WindowsEnvVarUpdater.windowsPathSeparator))
        setSomething = true
      }
    }

    setSomething
  }

  /** Removes all PATH entries that start with the given directory prefix.
    * This is used to clean up accumulated absolute JVM bin paths added by older coursier
    * versions before switching to the %JAVA_HOME%\bin reference approach.
    * The prefix comparison is case-insensitive and normalised to use backslashes,
    * as expected for Windows paths.
    */
  def removePathEntriesWithPrefix(prefix: String): Boolean = {
    val formerValueOpt = getEnvironmentVariable("PATH")
    formerValueOpt match {
      case None => false
      case Some(formerValue) =>
        // Normalise the prefix to a lowercase backslash-terminated string so we can do a
        // case-insensitive prefix match regardless of how the path was originally recorded.
        val normalizedPrefix =
          prefix.stripSuffix("\\").stripSuffix("/").toLowerCase(java.util.Locale.ROOT) + "\\"
        val parts    = formerValue.split(WindowsEnvVarUpdater.windowsPathSeparator, -1)
        val filtered = parts.filterNot(_.toLowerCase(java.util.Locale.ROOT).startsWith(normalizedPrefix))
        if (filtered.length != parts.length) {
          val newValue = filtered.filter(_.nonEmpty).mkString(WindowsEnvVarUpdater.windowsPathSeparator)
          if (newValue.isEmpty) clearEnvironmentVariable("PATH")
          else setEnvironmentVariable("PATH", newValue)
          true
        }
        else
          false
    }
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

  private[env] def windowsPathSeparator: String =
    ";"

  /** The PATH entry used to reference the active JVM's bin directory on Windows.
    * Using this expandable reference instead of an absolute path means that whenever
    * JAVA_HOME is updated, PATH automatically resolves to the new JVM's bin directory.
    */
  val javaHomeBinRef: String =
    "%JAVA_HOME%\\bin"

}
