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

  private def runUmaskAndCheck(command: String*): Unit = {
    val outputFile = os.temp(prefix = "coursier-umask-test", suffix = ".txt")
    try {
      // a non-default umask, so that a hard-coded fallback doesn't pass
      os.proc("sh", "-c", "umask 0027 && exec \"$@\"", "sh", command, "umask", outputFile)
        .call(stdin = os.Inherit, stdout = os.Inherit)
      val lines = os.read.lines(outputFile)
      // from Umask.get, then from the umask system call
      assert(lines == Seq("27", "27"))
    }
    finally
      os.remove(outputFile)
  }

  // Runs the test app in "fds" mode, and checks the exec'd command only inherited
  // the descriptors 0, 1, 2, and the one passed explicitly
  private def runFdsAndCheck(command: String*): Unit = {
    val outputFile = os.temp(prefix = "coursier-fds-test", suffix = ".txt")
    val tmpDir     = os.temp.dir(prefix = "coursier-fds-test")
    try {
      os.proc(command, "fds", outputFile, tmpDir).call(stdin = os.Inherit, stdout = os.Inherit)
      val lines = os.read.lines(outputFile)
      val inheritedFd = lines
        .collectFirst { case s"inherited-fd=$fd" => fd.toInt }
        .getOrElse(sys.error(s"No inherited-fd line in $lines"))
      val fds = lines.filter(_.forall(_.isDigit)).map(_.toInt).toSet
      // the descriptors of ls, that listed /dev/fd: those it inherited, plus the one
      // of the directory it lists
      val unexpectedFds = fds -- Set(0, 1, 2, inheritedFd)
      assert(fds.contains(inheritedFd))
      assert(unexpectedFds.size <= 1)
      assert(os.read.lines(tmpDir / "inherited") == Seq("inherited"))
      assert(lines.filter(_.startsWith("hook-")).sorted == Seq("hook-1", "hook-2"))
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
    // the java of the test JVM, rather than one from PATH
    val java = os.Path(sys.props("java.home")) / "bin/java"
    Seq(
      java.toString,
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

        test("fds-assembly") {
          runFdsAndCheck(assemblyCommand: _*)
        }

        test("fds-native-image") {
          runFdsAndCheck(nativeImage)
        }

        // These run in the test JVM: they must not get to the point where shutdown hooks run
        // or the working directory changes

        test("execve-invalid-fds") {
          // way above any descriptor limit
          val ex = assertThrows[ErrnoException] {
            Execve.execve("/bin/sh", Array("sh"), Array.empty[String], Array(100000000))
          }
          assert(ex.value() == 9)
          assert(ex.getMessage == "fcntl 100000000: Bad file descriptor (errno 9)")
          assertThrows[IllegalArgumentException] {
            Execve.execve("/bin/sh", Array("sh"), Array.empty[String], Array(-1))
          }
          assertThrows[NullPointerException] {
            Execve.execve("/bin/sh", Array("sh"), Array.empty[String], null: Array[Int])
          }
        }

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

        test("umask-assembly") {
          runUmaskAndCheck(assemblyCommand: _*)
        }

        test("umask-native-image") {
          runUmaskAndCheck(nativeImage)
        }

        test("umask-in-process") {
          val expected = Integer.parseInt(os.proc("sh", "-c", "umask").call().out.trim(), 8)
          assert(Umask.get() == expected)
          val viaSyscall = Umask.umask(Integer.parseInt("077", 8))
          Umask.umask(viaSyscall)
          assert(viaSyscall == expected)
          assert(Umask.get() == expected)
        }
      }
}
