package coursier.exec

import scala.util.Properties

import utest._

object ExecveTests extends TestSuite {

  private def runAndCheck(command: String*): Unit = {
    val outputFile = os.temp(prefix = "coursier-execve-test", suffix = ".txt")
    try {
      os.proc(command, outputFile).call(stdin = os.Inherit, stdout = os.Inherit)
      val lines = os.read.lines(outputFile)
      assert(lines.lastOption.contains("execve-target"))
      assert(lines.dropRight(1).sorted == Seq("hook-1", "hook-2"))
    }
    finally
      os.remove(outputFile)
  }

  private def runChdirAndCheck(command: String*): Unit = {
    val outputFile = os.temp(prefix = "coursier-chdir-test", suffix = ".txt")
    val targetDir  = os.temp.dir(prefix = "coursier-chdir-test")
    try {
      os.proc(command, outputFile, targetDir).call(stdin = os.Inherit, stdout = os.Inherit)
      val lines = os.read.lines(outputFile)
      // The exec'd command runs 'pwd -P', which prints the physical working directory,
      // so compare against the resolved (symlink-free) target directory.
      val expected = os.Path(targetDir.toNIO.toRealPath()).toString
      assert(lines.lastOption.contains(expected))
      assert(lines.dropRight(1).sorted == Seq("hook-1", "hook-2"))
    }
    finally {
      os.remove(outputFile)
      os.remove.all(targetDir)
    }
  }

  private def assemblyCommand = {
    val assembly = sys.props.getOrElse(
      "coursier.execve.test.assembly",
      sys.error("Java property coursier.execve.test.assembly not set")
    )
    Seq(
      "java",
      "--add-opens",
      "java.base/java.lang=ALL-UNNAMED",
      "-jar",
      assembly
    )
  }

  private def nativeImage = sys.props.getOrElse(
    "coursier.execve.test.native-image",
    sys.error("Java property coursier.execve.test.native-image not set")
  )

  val tests =
    if (Properties.isWin)
      Tests {}
    else
      Tests {
        test("assembly") {
          runAndCheck(assemblyCommand: _*)
        }

        test("native-image") {
          runAndCheck(nativeImage)
        }

        test("chdir-assembly") {
          runChdirAndCheck(assemblyCommand: _*)
        }

        test("chdir-native-image") {
          runChdirAndCheck(nativeImage)
        }
      }
}
