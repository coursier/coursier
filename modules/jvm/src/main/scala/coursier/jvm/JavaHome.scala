package coursier.jvm

import java.io.File
import java.nio.charset.Charset
import java.nio.file.{Files, Path}
import java.util.Locale

import coursier.cache.{Cache, CacheLogger}
import coursier.cache.internal.FileUtil
import coursier.env.EnvironmentUpdate
import coursier.util.Task
import dataclass.data

@data class JavaHome(
  cache: Option[JvmCache] = None,
  installIfNeeded: Boolean = true,
  getEnv: Option[String => Option[String]] = Some(k => Option(System.getenv(k))),
  os: String = JvmIndex.defaultOs(),
  commandOutput: JavaHome.CommandOutput = JavaHome.CommandOutput.default(),
  pathExtensions: Option[Seq[String]] = JavaHome.defaultPathExtensions
) {

  def withCache(cache: JvmCache): JavaHome =
    withCache(Some(cache))

  def withJvmCacheLogger(logger: JvmCacheLogger): JavaHome =
    withCache(
      cache.map(_.withDefaultLogger(logger))
    )
  def withCoursierCache(cache: Cache[Task]): JavaHome =
    withCache(
      this.cache.map(_.withCache(cache))
    )

  def withDefaultCache: Task[JavaHome] =
    JvmCache.default.map(withCache(_))


  def default(): Task[File] =
    get(JavaHome.defaultId)

  def system(): Task[Option[File]] =
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

            val outputOrRetCode = commandOutput
              .run(Seq("java", "-XshowSettings:properties", "-version"), keepErrStream = true)

            outputOrRetCode
              .toOption
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

  def get(id: String): Task[File] =
    getWithRetainedId(id)
      .map(_._2)

  def getWithRetainedId(id: String): Task[(String, File)] =
    if (id == JavaHome.systemId)
      system().flatMap {
        case None => Task.fail(new Exception("No system JVM found"))
        case Some(dir) => Task.point(JavaHome.systemId -> dir)
      }
    else if (id.startsWith(JavaHome.systemId + "|"))
      system().flatMap {
        case None => getWithRetainedId(id.stripPrefix(JavaHome.systemId + "|"))
        case Some(dir) => Task.point(JavaHome.systemId -> dir)
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
          cache0.get(id0).map(id -> _)
      }
    }

  def javaBin(id: String): Task[Path] =
    get(id).flatMap { home =>
      JavaHome.javaBin(home.toPath, pathExtensions) match {
        case Some(exe) => Task.point(exe)
        case None => Task.fail(new Exception(s"${new File(home, "java/bin")} not found"))
      }
    }

  def environmentFor(id: String): Task[EnvironmentUpdate] =
    get(id).map { home =>
      JavaHome.environmentFor(id, home, isMacOs = os == "darwin")
    }

  def environmentFor(id: String, home: File): EnvironmentUpdate =
    JavaHome.environmentFor(id, home, isMacOs = os == "darwin")

}

object JavaHome {

  // TODO Load the content of JvmIndex lazily / later, and only if it is required
  // (it's not when looking at the system JVM for example), so that we can get
  // this one as is, rather than wrapped in Task[_].
  def default: Task[JavaHome] =
    JavaHome().withDefaultCache

  def systemId: String =
    "system"
  def defaultJvm: String =
    "adopt@1.8+"
  def defaultId: String =
    s"$systemId|$defaultJvm"

  def environmentFor(
    id: String,
    javaHome: File,
    isMacOs: Boolean
  ): EnvironmentUpdate =
    if (id == systemId)
      EnvironmentUpdate.empty
    else {
      val addPath = !isMacOs // /usr/bin/java reads JAVA_HOME on macOS, no need to update the PATH
      val pathEnv =
        if (addPath) {
          val binDir = new File(javaHome, "bin").getAbsolutePath
          EnvironmentUpdate.empty.withPathLikeAppends(Seq("PATH" -> binDir))
        } else
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
        pathExtensions
          .toStream
          .map(ext => dir.resolve(name + ext))
          .filter(Files.exists(_))
          .headOption
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

  trait CommandOutput {
    def run(command: Seq[String], keepErrStream: Boolean): Either[Int, String]
  }

  object CommandOutput {
    private final class DefaultCommandOutput extends CommandOutput {
      def run(command: Seq[String], keepErrStream: Boolean): Either[Int, String] = {
        val b = new ProcessBuilder(command: _*)
        b.redirectInput(ProcessBuilder.Redirect.INHERIT)
        b.redirectOutput(ProcessBuilder.Redirect.PIPE)
        b.redirectError(ProcessBuilder.Redirect.PIPE)
        b.redirectErrorStream(true)
        val p = b.start()
        p.getOutputStream.close()
        val output = new String(FileUtil.readFully(p.getInputStream), Charset.defaultCharset())
        val retCode = p.waitFor()
        if (retCode == 0)
          Right(output)
        else
          Left(retCode)
      }
    }

    def default(): CommandOutput =
      new DefaultCommandOutput

    def apply(f: (Seq[String], Boolean) => Either[Int, String]): CommandOutput =
      new CommandOutput {
        def run(command: Seq[String], keepErrStream: Boolean): Either[Int, String] =
          f(command, keepErrStream)
      }
  }

}
