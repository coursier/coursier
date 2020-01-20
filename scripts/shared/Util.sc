
import java.io.File
import java.nio.file.{Files, Path}

import scala.collection.JavaConverters._

private def updateCommand(cmd: Seq[String]): Seq[String] =
  if (os == "win" && cmd.nonEmpty) {
    def candidates(name: String) =
      Iterator(name + ".bat", name + ".exe", name + ".cmd", name)

    if (!cmd.head.contains("/") && !cmd.head.contains("\\")) {
      def pathDirectories = Option(System.getenv("PATH"))
        .getOrElse {
          System.err.println("Warning: cannot get PATH")
          ""
        }
        .split(File.pathSeparatorChar)
        .iterator
        .filter(_.nonEmpty)
        .map(new File(_))

      // not really sure what would be the correct or recommended or conventional order for suffixes here

      val fullPath = pathDirectories
        .flatMap(dir => candidates(cmd.head).map(n => new File(dir, n)))
        .collectFirst {
          case f if f.isFile =>
            f.getAbsolutePath
        }
        .getOrElse {
          System.err.println(s"Warning: cannot find ${cmd.head} in PATH")
          cmd.head
        }

      fullPath +: cmd.tail
    } else {
      val abs = new File(cmd.head).getAbsoluteFile
      val dir = abs.getParentFile
      val name = abs.getName
      val fullPath = candidates(name)
        .map(n => new File(dir, n))
        .collectFirst {
          case f if f.isFile =>
            f.getAbsolutePath
        }
        .getOrElse {
          System.err.println(s"Warning: cannot find ${cmd.head}")
          cmd.head
        }

      fullPath +: cmd.tail
    }
  } else
    cmd

/**
 * Tries to run a command.
 *
 * @return Whether the command was successful.
 */
def tryRun(cmd: Seq[String]): Boolean = {
  val b = new ProcessBuilder(updateCommand(cmd): _*)
    .inheritIO()
  val p = b.start()
  val retCode = p.waitFor()
  retCode == 0
}

/**
 * Runs a command.
 *
 * Throws if the command fails.
 */
def run(cmd: Seq[String]): Unit =
  run(cmd, None, Nil)

/**
 * Runs a command with additional variables in the environment.
 *
 * Throws if the command fails.
 */
def run(cmd: Seq[String], extraEnv: Seq[(String, String)]): Unit =
  run(cmd, None, extraEnv)

/**
 * Runs a command from a particular directory.
 *
 * Throws if the command fails.
 *
 * @param from Directory to run the command from.
 */
def run(cmd: Seq[String], from: File): Unit =
  run(cmd, Some(from), Nil)

private def run(cmd: Seq[String], fromOpt: Option[File], extraEnv: Seq[(String, String)]): Unit = {
  val b = new ProcessBuilder(updateCommand(cmd): _*)
    .inheritIO()
  for (dir <- fromOpt)
    b.directory(dir)
  if (extraEnv.nonEmpty) {
    val map = b.environment()
    for ((k, v) <- extraEnv)
      map.put(k, v)
  }
  val p = b.start()
  val retCode = p.waitFor()
  if (retCode != 0)
    sys.error(s"Command ${b.command.asScala.mkString(" ")} exited with return code $retCode")
}

/**
 * Current OS we're running on.
 *
 * @return Either one of `"linux"`, `"mac"`, `"win"`.
 */
lazy val os: String = {
  val p = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT)
  if (p.contains("linux")) "linux"
  else if (p.contains("mac")) "mac"
  else if (p.contains("windows")) "win"
  else sys.error(s"Unrecognized OS: $p")
}

/**
 * Runs a command, and returns its output.
 */
def output(cmd: Seq[String]): String = {
  val cmd0 = updateCommand(cmd)
  val p = new ProcessBuilder(cmd0: _*)
    .redirectInput(ProcessBuilder.Redirect.INHERIT)
    .redirectOutput(ProcessBuilder.Redirect.PIPE)
    .redirectError(ProcessBuilder.Redirect.INHERIT)
    .start()
  val output = scala.io.Source.fromInputStream(p.getInputStream).mkString
  val retCode = p.waitFor()
  if (retCode == 0)
    output
  else
    sys.error(s"Command ${cmd0.mkString("\n")} exited with return code $retCode")
}

/**
 * Whether the passed git repository has non committed changes.
 */
def gitRepoHasChanges(repo: File): Boolean = {
  val p = new ProcessBuilder(updateCommand(Seq("git", "status")): _*)
    .directory(repo)
    .redirectInput(ProcessBuilder.Redirect.INHERIT)
    .redirectOutput(ProcessBuilder.Redirect.PIPE)
    .redirectError(ProcessBuilder.Redirect.PIPE)
    .redirectErrorStream(true)
    .start()
  val output = scala.io.Source.fromInputStream(p.getInputStream).mkString
  val retCode = p.waitFor()
  if (retCode == 0)
    !output.contains("nothing to commit")
  else
    sys.error(s"Command git status exited with return code $retCode")
}

/**
 * Performs an action with a temporary directory.
 *
 * The temporary directory is deleted upon completion.
 */
def withTmpDir[T](prefix: String)(f: Path => T): T = {
  var tmpDir: Path = null
  try {
    tmpDir = Files.createTempDirectory(prefix)
    f(tmpDir)
  } finally {
    if (tmpDir != null) {
      System.err.println(s"Deleting $tmpDir")
      deleteRecursively(tmpDir)
    }
  }
}

private def deleteRecursively(p: Path): Unit = {
  if (Files.isDirectory(p))
    p.toFile
      .listFiles
      .map(_.toPath)
      .foreach(deleteRecursively)

  Files.deleteIfExists(p)
}


def withBgProcess[T](
  cmd: Seq[String],
  dir: File = new File(".")
)(f: => T): T = {

  val b = new ProcessBuilder(updateCommand(cmd): _*)
  b.inheritIO()
  b.directory(dir)

  System.err.println(s"Running ${cmd.mkString(" ")}")
  val p = b.start()

  try f
  finally {
    p.destroy()
    p.waitFor(1L, java.util.concurrent.TimeUnit.SECONDS)
    p.destroyForcibly()
  }
}
