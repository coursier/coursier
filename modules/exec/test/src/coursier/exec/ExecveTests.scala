package coursier.exec

import java.io.FileNotFoundException

import scala.util.Properties

import utest._

object ExecveTests extends TestSuite {

  private def runAndCheck(command: String*): Unit = {
    val outputFile = os.temp(prefix = "coursier-execve-test", suffix = ".txt")
    try {
      os.proc(command, "execve", outputFile).call(stdin = os.Inherit, stdout = os.Inherit)
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
      os.proc(command, "chdir", outputFile, targetDir).call(stdin = os.Inherit, stdout = os.Inherit)
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

  // Runs the test app with a path that doesn't exist, and checks the error it reports
  private def runMissingAndCheck(mode: String, syscall: String, command: String*): Unit = {
    val outputFile = os.temp(prefix = s"coursier-$mode-test", suffix = ".txt")
    val tmpDir     = os.temp.dir(prefix = s"coursier-$mode-test")
    val missing    = tmpDir / "missing"
    try {
      os.proc(command, mode, outputFile, missing).call(stdin = os.Inherit, stdout = os.Inherit)
      val lines = os.read.lines(outputFile)
      val (errorLines, hookLines) = lines.partition(line =>
        line.startsWith("errno=") || line.startsWith("cause=") || line.startsWith("message=")
      )
      val expectedErrorLines = Seq(
        "errno=2",
        "cause=java.io.FileNotFoundException",
        s"message=$syscall $missing: No such file or directory (errno 2)"
      )
      assert(errorLines == expectedErrorLines)
      // shutdown hooks run once, before execve, or when the app exits after chdir failed
      assert(hookLines.sorted == Seq("hook-1", "hook-2"))
    }
    finally {
      os.remove(outputFile)
      os.remove.all(tmpDir)
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
      Tests {
        test("not available") {
          assert(!Execve.available())
          assert(!Chdir.available())
        }
      }
    else
      Tests {
        test("available") {
          assert(Execve.available())
          assert(Chdir.available())
        }

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

        test("execve-missing-assembly") {
          runMissingAndCheck("execve-missing", "execve", assemblyCommand: _*)
        }

        test("execve-missing-native-image") {
          runMissingAndCheck("execve-missing", "execve", nativeImage)
        }

        test("chdir-missing-assembly") {
          runMissingAndCheck("chdir-missing", "chdir", assemblyCommand: _*)
        }

        test("chdir-missing-native-image") {
          runMissingAndCheck("chdir-missing", "chdir", nativeImage)
        }

        // These run in the test JVM: they must not get to the point where shutdown hooks run
        // or the working directory changes

        test("chdir-missing-in-process") {
          val tmpDir  = os.temp.dir(prefix = "coursier-chdir-test")
          val missing = tmpDir / "missing"
          try {
            val ex = assertThrows[ErrnoException](Chdir.chdir(missing.toString))
            assert(ex.value() == 2)
            val causeIsFileNotFound = ex.getCause match {
              case _: FileNotFoundException => true
              case _                        => false
            }
            assert(causeIsFileNotFound)
            assert(ex.getMessage == s"chdir $missing: No such file or directory (errno 2)")
          }
          finally os.remove.all(tmpDir)
        }

        test("execve-null-arguments") {
          assertThrows[NullPointerException] {
            Execve.execve("/bin/sh", Array("sh", null), Array.empty[String])
          }
          assertThrows[NullPointerException] {
            Execve.execve("/bin/sh", Array("sh"), Array[String](null))
          }
          assertThrows[NullPointerException] {
            Execve.execve(null, Array("sh"))
          }
        }

        test("errno-exception-message") {
          assert(new ErrnoException(2).getMessage == "No such file or directory (errno 2)")
          assert(
            new ErrnoException("foo", 2, null).getMessage ==
              "foo: No such file or directory (errno 2)"
          )
        }
      }
}
