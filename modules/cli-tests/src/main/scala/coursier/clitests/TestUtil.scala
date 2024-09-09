package coursier.clitests

import java.io.{File, FileWriter}
import java.nio.file.Files

import coursier.util.StringInterpolators._

object TestUtil {

  def withTempDir[T](testCode: File => T): T =
    withTempDir("coursier-cli-test")(testCode)

  def withTempDir[T](prefix: String)(testCode: File => T): T = {
    val dir = Files.createTempDirectory(prefix).toFile
    try testCode(dir)
    finally cleanDir(dir)
  }

  private def cleanDir(tmpDir: File): Unit = {
    def delete(f: File): Boolean =
      if (f.isDirectory) {
        val removedContent =
          Option(f.listFiles()).toSeq.flatten.map(delete).forall(x => x)
        val removedDir = f.delete()

        removedContent && removedDir
      }
      else
        f.delete()

    if (!delete(tmpDir))
      Console.err.println(
        s"Warning: unable to remove temporary directory $tmpDir"
      )
  }

  val propsDep    = dep"io.get-coursier:props:1.0.2"
  val propsDepStr = s"${propsDep.module}:${propsDep.version}"
  lazy val propsCp = coursier.Fetch()
    .addDependencies(propsDep)
    .run()
    .map(_.getAbsolutePath)

  // TODO Fetch snailgun instead?
  lazy val ngCommand = {
    val pathDirs = Option(System.getenv("PATH"))
      .getOrElse("")
      .split(File.pathSeparator)
      .filter(_.nonEmpty)
      .map(new File(_))
    val ngNailgunFound = pathDirs
      .iterator
      .map(dir => new File(dir, "ng-nailgun"))
      // TODO check if executable on Linux and macOS
      // TODO use PATHEXT on Windows
      .exists(_.isFile)
    if (ngNailgunFound) "ng-nailgun"
    else "ng"
  }

  lazy val scalaCli = sys.props.getOrElse(
    "coursier-test.scala-cli",
    sys.error("Java property coursier-test.scala-cli not set")
  )

}
