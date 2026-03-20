package coursier.env

import java.io.IOException
import java.nio.file.{Files, InvalidPathException, Path, Paths}
import java.util.Locale

import dataclass.{data, since => unroll}

import scala.jdk.CollectionConverters._

@data case class WindowsEnvVarUpdater(
  powershellRunner: PowershellRunner = PowershellRunner(),
  target: String = "User",
  @unroll
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

  private def setPathLike(name: String, formerEntries: Seq[String], newEntries: Seq[String])
    : Boolean =
    newEntries != formerEntries && {
      if (newEntries.isEmpty) clearEnvironmentVariable(name)
      else setEnvironmentVariable(name, WindowsEnvVarUpdater.joinPathLike(newEntries))
      true
    }

  /** Whether we can put [[WindowsEnvVarUpdater.javaHomeBinRef]] in the PATH, rather than the bin
    * directory of the JVM being set up.
    *
    * Only makes sense if values are written as `REG_EXPAND_SZ`, which the JNI-based implementation
    * does, but not the powershell one.
    */
  private def canReferenceJavaHome: Boolean =
    useJni0

  def applyUpdate(update: EnvironmentUpdate): Boolean = {

    // Beware, these are not an atomic operation overall
    // (we might discard values added by others between our get and our set)

    var setSomething = false

    val newJavaHomeOpt = update.set.collectFirst { case ("JAVA_HOME", value) => value }
    // Read while JAVA_HOME still has its former value, the loop right below overwrites it.
    // Needed to get rid of the PATH entry pointing at the JVM we're moving away from.
    val formerJavaHomeOpt = newJavaHomeOpt.flatMap(_ => getEnvironmentVariable("JAVA_HOME"))

    for ((k, v) <- update.set) {
      val formerValueOpt = getEnvironmentVariable(k)
      val needsUpdate    = formerValueOpt.forall(_ != v)
      if (needsUpdate) {
        setEnvironmentVariable(k, v)
        setSomething = true
      }
    }

    for ((k, v) <- update.pathLikeAppends) {
      val formerEntries = WindowsEnvVarUpdater.splitPathLike(getEnvironmentVariable(k))
      val newEntries =
        if (canReferenceJavaHome && WindowsEnvVarUpdater.isJavaHomeBinDir(k, v, newJavaHomeOpt))
          WindowsEnvVarUpdater.withJavaHomeBinRef(formerEntries, v, formerJavaHomeOpt)
        else
          WindowsEnvVarUpdater.appended(formerEntries, v)
      if (setPathLike(k, formerEntries, newEntries))
        setSomething = true
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

    val javaHomeOpt = update.set.collectFirst { case ("JAVA_HOME", value) => value }

    for ((k, v) <- update.pathLikeAppends; formerValue <- getEnvironmentVariable(k)) {
      // Depending on the coursier version that added it, and on whether it went through JNI,
      // the entry can be the JVM bin directory itself or a reference to JAVA_HOME.
      val toRemove =
        if (WindowsEnvVarUpdater.isJavaHomeBinDir(k, v, javaHomeOpt))
          Seq(v, WindowsEnvVarUpdater.javaHomeBinRef)
        else
          Seq(v)
      val formerEntries = WindowsEnvVarUpdater.splitPathLike(Some(formerValue))
      if (setPathLike(k, formerEntries, WindowsEnvVarUpdater.removed(formerEntries, toRemove)))
        setSomething = true
    }

    setSomething
  }

  /** Removes from the PATH the JVM bin directories living under `prefix`.
    *
    * Former coursier versions added the bin directory of the JVM they were setting up to the PATH.
    * Setting up another JVM added a new entry rather than replacing the former one, so that the JVM
    * set up first kept winning, whatever JAVA_HOME says. Passing the JVM archive cache directory
    * here gets rid of all of those in one go.
    */
  def removePathEntriesWithPrefix(prefix: String): Boolean = {
    val formerEntries = WindowsEnvVarUpdater.splitPathLike(getEnvironmentVariable("PATH"))
    val newEntries = WindowsEnvVarUpdater.withoutJvmBinDirsUnder(
      formerEntries,
      prefix,
      WindowsEnvVarUpdater.isJvmBinDir(_, WindowsEnvVarUpdater.pathExtensions)
    )
    setPathLike("PATH", formerEntries, newEntries)
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

  /** PATH entry standing for the bin directory of the JVM JAVA_HOME points at.
    *
    * Adding that rather than the bin directory itself means updating JAVA_HOME is enough to switch
    * JVMs, and that setting up several JVMs one after the other doesn't pile up entries in the
    * PATH.
    *
    * Windows only expands those references in values stored as `REG_EXPAND_SZ`, which
    * [[coursier.jniutils.WindowsEnvironmentVariables]] does, but not the powershell fallback of
    * [[WindowsEnvVarUpdater]].
    */
  def javaHomeBinRef: String =
    "%JAVA_HOME%\\bin"

  private[env] def splitPathLike(valueOpt: Option[String]): Seq[String] =
    valueOpt.filter(_.nonEmpty).fold(Seq.empty[String]) { value =>
      value.split(windowsPathSeparator, -1).toSeq
    }

  private[env] def joinPathLike(entries: Seq[String]): String =
    entries.mkString(windowsPathSeparator)

  private def binDirsOf(javaHome: String): Seq[String] =
    Seq(javaHome + "\\bin", javaHome + "/bin")

  /** Whether appending `entry` to the `name` environment variable amounts to putting the bin
    * directory of the JVM `javaHomeOpt` points at on the PATH
    */
  private[env] def isJavaHomeBinDir(
    name: String,
    entry: String,
    javaHomeOpt: Option[String]
  ): Boolean =
    name == "PATH" &&
    javaHomeOpt.exists(binDirsOf(_).exists(_.equalsIgnoreCase(entry)))

  private[env] def appended(entries: Seq[String], entry: String): Seq[String] =
    if (entries.contains(entry)) entries
    else entries :+ entry

  private[env] def removed(entries: Seq[String], toRemove: Seq[String]): Seq[String] =
    entries.filterNot(entry => toRemove.exists(_.equalsIgnoreCase(entry)))

  /** Adds [[javaHomeBinRef]] to `entries`, getting rid of the entries pointing at the bin directory
    * of either the JVM being set up, or the one JAVA_HOME used to point at
    */
  private[env] def withJavaHomeBinRef(
    entries: Seq[String],
    binDir: String,
    formerJavaHomeOpt: Option[String]
  ): Seq[String] = {
    val kept = removed(entries, binDir +: formerJavaHomeOpt.toSeq.flatMap(binDirsOf))
    if (kept.exists(_.equalsIgnoreCase(javaHomeBinRef))) kept
    else kept :+ javaHomeBinRef
  }

  private[env] def pathExtensions: Seq[String] =
    Option(System.getenv("PATHEXT"))
      .map(_.split(windowsPathSeparator).toSeq.filter(_.nonEmpty))
      .filter(_.nonEmpty)
      .getOrElse(Seq(".exe", ".cmd", ".com", ".bat"))

  /** Whether `dir` is the bin directory of a JVM, that is it has a java executable, and its parent
    * directory has a release file with a JAVA_VERSION field
    */
  private[env] def isJvmBinDir(dir: Path, pathExtensions: Seq[String]): Boolean = {
    def hasJavaExecutable =
      pathExtensions.exists(ext => Files.isRegularFile(dir.resolve("java" + ext)))
    def hasReleaseFile =
      Option(dir.getParent).exists { parent =>
        val releaseFile = parent.resolve("release")
        Files.isRegularFile(releaseFile) && {
          try Files.readAllLines(releaseFile).asScala.exists(_.startsWith("JAVA_VERSION="))
          catch { case _: IOException => false }
        }
      }
    hasJavaExecutable && hasReleaseFile
  }

  /** Removes from `entries` the JVM bin directories living under the `prefix` directory */
  private[env] def withoutJvmBinDirsUnder(
    entries: Seq[String],
    prefix: String,
    isJvmBinDir: Path => Boolean
  ): Seq[String] = {
    val prefix0 =
      prefix.replace('/', '\\').stripSuffix("\\").toLowerCase(Locale.ROOT) + "\\"
    entries.filterNot { entry =>
      entry.replace('/', '\\').toLowerCase(Locale.ROOT).startsWith(prefix0) &&
      (try isJvmBinDir(Paths.get(entry))
      catch { case _: InvalidPathException => false })
    }
  }

}
