package coursier.jvm

import java.io.{File, IOException}
import java.nio.charset.Charset
import java.nio.file.{Files, Path}
import java.util.Locale

import coursier.cache.{ArchiveCache, Cache, CacheLogger}
import coursier.cache.internal.FileUtil
import coursier.env.EnvironmentUpdate
import coursier.jvm.util.CommandOutput
import coursier.util.Task
import dataclass._

@data class JavaHome(
  cache: Option[JvmCache] = None,
  getEnv: Option[String => Option[String]] = Some(k => Option(System.getenv(k))),
  os: String = JvmIndex.defaultOs(),
  commandOutput: CommandOutput = CommandOutput.default(),
  pathExtensions: Option[Seq[String]] = JavaHome.defaultPathExtensions,
  allowSystem: Boolean = true,
  @since
  update: Boolean = false,
  noUpdateCache: Option[JvmCache] = None
) {

  def withCache(cache: JvmCache): JavaHome =
    withCache(Some(cache))

  def withArchiveCache(archiveCache: ArchiveCache[Task]): JavaHome =
    withCache(
      this.cache.map(_.withArchiveCache(archiveCache))
    )

  def default(): Task[File] =
    get(JavaHome.defaultId)

  def system(): Task[Option[File]] =
    if (allowSystem)
      Task.delay(getEnv.flatMap(_("JAVA_HOME"))).flatMap {
        case None =>
          if (os == "darwin")
            Task.delay {
              // FIXME What happens if no JDK is installed?
              val outputOrRetCode = commandOutput
                .run(Seq("/usr/libexec/java_home"), keepErrStream = false)
              outputOrRetCode
                .toOption
                .map(_.trim)
                .filter(_.nonEmpty)
                .map(new File(_))
            }
          else
            Task.delay {

              val outputOrRetCode =
                try commandOutput
                  .run(
                    Seq("java", "-XshowSettings:properties", "-version"),
                    keepErrStream = true,
                    // Setting this makes cs-java fail.
                    // This prevents us (possibly cs-java) to call ourselves,
                    // which could call ourselves again, etc. indefinitely.
                    extraEnv = Seq(JavaHome.csJavaFailVariable -> "true")
                  )
                  .toOption
                catch {
                  case _: IOException =>
                    None
                }

              outputOrRetCode
                .flatMap { output =>
                  val it = output
                    .linesIterator
                    .map(_.trim)
                    .filter(_.startsWith("java.home = "))
                    .map(_.stripPrefix("java.home = "))
                  if (it.hasNext)
                    Some(it.next())
                      .map(new File(_))
                  else
                    None
                }
            }
        case Some(home) =>
          Task.point(Some(new File(home)))
      }
    else
      Task.point(None)

  def getIfInstalled(id: String): Task[Option[File]] =
    getWithIsSystemIfInstalled(id)
      .map(_.map(_._2))

  def getWithIsSystemIfInstalled(id: String): Task[Option[(Boolean, File)]] =
    if (id == JavaHome.systemId)
      system().map(_.map(true -> _))
    else if (id.startsWith(JavaHome.systemId + "|"))
      system().flatMap {
        case None      => getWithIsSystemIfInstalled(id.stripPrefix(JavaHome.systemId + "|"))
        case Some(dir) => Task.point(Some(true -> dir))
      }
    else
      noUpdateCache.orElse(cache) match {
        case None => Task.point(None)
        case Some(cache0) =>
          val id0 =
            if (id == JavaHome.defaultId)
              JavaHome.defaultJvm
            else
              id

          cache0.getIfInstalled(id0).map(_.map((false, _)))
      }

  def get(id: String): Task[File] =
    getWithIsSystem(id)
      .map(_._2)

  def getWithIsSystem(id: String): Task[(Boolean, File)] =
    if (update)
      getWithIsSystem0(id)
    else
      getWithIsSystemIfInstalled(id).flatMap {
        case Some(res) => Task.point(res)
        case None      => getWithIsSystem0(id)
      }

  private def getWithIsSystem0(id: String): Task[(Boolean, File)] =
    if (id == JavaHome.systemId)
      system().flatMap {
        case None      => Task.fail(new Exception("No system JVM found"))
        case Some(dir) => Task.point(true -> dir)
      }
    else if (id.startsWith(JavaHome.systemId + "|"))
      system().flatMap {
        case None      => getWithIsSystem(id.stripPrefix(JavaHome.systemId + "|"))
        case Some(dir) => Task.point(true -> dir)
      }
    else {
      val id0 =
        if (id == JavaHome.defaultId)
          JavaHome.defaultJvm
        else
          id

      cache match {
        case None => Task.fail(new Exception("No JVM cache passed"))
        case Some(cache0) =>
          cache0.get(id0).map(home => false -> home)
      }
    }

  def javaBin(id: String): Task[Path] =
    get(id).flatMap { home =>
      JavaHome.javaBin(home.toPath, pathExtensions) match {
        case Some(exe) => Task.point(exe)
        case None      => Task.fail(new Exception(s"${new File(home, "java/bin")} not found"))
      }
    }

  def environmentFor(id: String): Task[EnvironmentUpdate] =
    getWithIsSystem(id).map {
      case (isSystem, home) =>
        JavaHome.environmentFor(isSystem, home, isMacOs = os == "darwin")
    }

  def environmentFor(isSystem: Boolean, home: File): EnvironmentUpdate =
    JavaHome.environmentFor(isSystem, home, isMacOs = os == "darwin")

}

object JavaHome {

  def systemId: String =
    "system"
  def defaultJvm: String =
    "adopt@1.8+"
  def defaultId: String =
    s"$systemId|$defaultJvm"

  def environmentFor(
    isSystem: Boolean,
    javaHome: File,
    isMacOs: Boolean
  ): EnvironmentUpdate =
    if (isSystem)
      EnvironmentUpdate.empty
    else {
      val addPath = !isMacOs // /usr/bin/java reads JAVA_HOME on macOS, no need to update the PATH
      val pathEnv =
        if (addPath) {
          val binDir = new File(javaHome, "bin").getAbsolutePath
          EnvironmentUpdate.empty.withPathLikeAppends(Seq("PATH" -> binDir))
        }
        else
          EnvironmentUpdate.empty
      EnvironmentUpdate.empty.withSet(Seq("JAVA_HOME" -> javaHome.getAbsolutePath)) + pathEnv
    }

  private def executable(
    dir: Path,
    name: String,
    pathExtensionsOpt: Option[Seq[String]]
  ): Option[Path] =
    pathExtensionsOpt match {
      case Some(pathExtensions) =>
        val it = pathExtensions
          .iterator
          .map(ext => dir.resolve(name + ext))
          .filter(Files.exists(_))
        if (it.hasNext)
          Some(it.next())
        else
          None
      case None =>
        Some(dir.resolve(name))
    }

  def javaBin(
    javaHome: Path,
    pathExtensionsOpt: Option[Seq[String]]
  ): Option[Path] =
    executable(javaHome.resolve("bin"), "java", pathExtensionsOpt)

  def defaultPathExtensions: Option[Seq[String]] = {
    val isWindows = System.getProperty("os.name", "")
      .toLowerCase(Locale.ROOT)
      .contains("windows")
    if (isWindows)
      Option(System.getenv("pathext"))
        .map(_.split(File.pathSeparator).toSeq)
    else
      None
  }

  private[coursier] def csJavaFailVariable: String =
    "__CS_JAVA_FAIL"

  private def maybeRemovePath(
    cacheDirectory: Path,
    getEnv: String => Option[String],
    pathSeparator: String
  ): Option[String] = {
    val fs = cacheDirectory.getFileSystem
    // remove former Java bin dir from PATH
    for {
      previousPath <- getEnv("PATH")
      previousHome <- getEnv("JAVA_HOME").map(fs.getPath(_))
      if previousHome.startsWith(cacheDirectory)
      previousPath0 = previousPath.split(pathSeparator)
      removeIdx = previousPath0.indexWhere { entry =>
        val p0 = fs.getPath(entry)
        // FIXME Make that more strict?
        p0.startsWith(previousHome) && p0.endsWith("bin")
      }
      if removeIdx >= 0
    } yield {
      val newPath = previousPath0.take(removeIdx) ++ previousPath0.drop(removeIdx + 1)
      // FIXME Not sure escaping is fine in all cases…
      s"""export PATH="${newPath.mkString(pathSeparator)}"""" + "\n"
    }
  }

  def finalBashScript(
    envUpdate: EnvironmentUpdate,
    getEnv: String => Option[String] = k => Option(System.getenv(k)),
    pathSeparator: String = File.pathSeparator
  ): String = {

    val preamble =
      if (getEnv("CS_FORMER_JAVA_HOME").isEmpty) {
        val saveJavaHome = """export CS_FORMER_JAVA_HOME="$JAVA_HOME""""
        saveJavaHome + "\n"
      }
      else
        ""

    preamble + envUpdate.bashScript + "\n"
  }

  def finalBatScript(
    envUpdate: EnvironmentUpdate,
    getEnv: String => Option[String] = k => Option(System.getenv(k)),
    pathSeparator: String = File.pathSeparator
  ): String = {

    val preamble =
      if (getEnv("CS_FORMER_JAVA_HOME").isEmpty) {
        val saveJavaHome = """set CS_FORMER_JAVA_HOME="%JAVA_HOME%""""
        saveJavaHome + "\r\n"
      }
      else
        ""

    preamble + envUpdate.batScript + "\r\n"
  }

  def disableBashScript(
    getEnv: String => Option[String] = k => Option(System.getenv(k)),
    pathSeparator: String = ":",
    isMacOs: Boolean = JvmIndex.defaultOs() == "darwin"
  ): String =
    getEnv("CS_FORMER_JAVA_HOME") match {
      case None => ""
      case Some("") =>
        """unset JAVA_HOME""" + "\n" +
          """unset CS_FORMER_JAVA_HOME""" + "\n"
      case Some(_) =>
        """export JAVA_HOME="$CS_FORMER_JAVA_HOME"""" + "\n" +
          """unset CS_FORMER_JAVA_HOME""" + "\n"
    }

  def disableBatScript(
    getEnv: String => Option[String] = k => Option(System.getenv(k)),
    pathSeparator: String = ";"
  ): String =
    getEnv("CS_FORMER_JAVA_HOME") match {
      case None => ""
      case Some("") =>
        """set JAVA_HOME=""" + "\r\n" +
          """set CS_FORMER_JAVA_HOME=""" + "\r\n"
      case Some(_) =>
        """set "JAVA_HOME=%CS_FORMER_JAVA_HOME%"""" + "\r\n" +
          """set CS_FORMER_JAVA_HOME=""" + "\r\n"
    }

}
