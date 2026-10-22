package coursier.launcher

import java.nio.charset.StandardCharsets

import scala.util.Properties

import utest._

object PreambleQuotingTests extends TestSuite {

  /** Values that break out of naive quoting, in sh or cmd */
  private val nastyValues = Seq(
    "-Dsingle='quote",
    "-Dcmd=$(touch pwned-dollar)",
    "-Dbackticks=`touch pwned-backticks`",
    "-Dvar=$HOME ${HOME}",
    "-Ddouble=\"quote",
    "-Dbackslash=\\ \\\\",
    "-Dsemicolon=a; touch pwned-semicolon",
    "-Dglob=*",
    "-Dnewline=a\ntouch pwned-newline",
    "-Dmix=';touch pwned-mix;'\"`$"
  )

  private def readArgs(file: os.Path): Seq[String] =
    new String(os.read.bytes(file), StandardCharsets.UTF_8).split("\u0000", -1).toSeq.init

  /** Runs `preamble` as a launcher, with a fake java that dumps its arguments
    *
    * Returns the arguments that fake java got, and the value it saw for `CS_TEST_VAR`.
    */
  private def runSh(preamble: Preamble, setupDir: os.Path => Unit = _ => ()): (
    Seq[String],
    String
  ) = {
    val tmpDir = os.temp.dir(prefix = "cs-preamble-quoting")
    try {
      val javaHome = tmpDir / "java-home"
      os.write(
        javaHome / "bin" / "java",
        """#!/bin/sh
          |printf '%s\0' "$@" > "$CS_TEST_OUT/args"
          |printf '%s' "$CS_TEST_VAR" > "$CS_TEST_OUT/var"
          |""".stripMargin,
        perms = "rwxr-xr-x",
        createFolders = true
      )
      val work = tmpDir / "work"
      os.makeDir(work)
      setupDir(work)
      val launcher = tmpDir / "launcher"
      os.write(launcher, preamble.withOsKind(false).value, perms = "rwxr-xr-x")
      val res = os.proc(launcher, "app-arg").call(
        cwd = work,
        env = Map(
          "JAVA_HOME"   -> javaHome.toString,
          "JAVA_OPTS"   -> null,
          "CS_TEST_OUT" -> tmpDir.toString,
          "CS_TEST_VAR" -> null
        ),
        check = false,
        mergeErrIntoOut = true
      )
      assert(res.exitCode == 0)
      val pwned = os.list(work).map(_.last).filter(_.startsWith("pwned"))
      assert(pwned.isEmpty)
      (readArgs(tmpDir / "args"), os.read(tmpDir / "var"))
    }
    finally os.remove.all(tmpDir)
  }

  // these tests run the generated scripts with sh
  private def unixOnly(f: => Unit): Unit =
    if (!Properties.isWin) f

  val tests = Tests {

    test("sh") {
      test("java options") {
        unixOnly {
          val (args, _) = runSh(Preamble().copy(javaOpts = nastyValues))
          val expected  = nastyValues ++ Seq("-jar")
          assert(args.take(expected.length) == expected)
          assert(args.last == "app-arg")
        }
      }

      test("env vars") {
        unixOnly {
          for (value <- nastyValues) {
            val (_, var0) = runSh(Preamble().addExtraEnvVar("CS_TEST_VAR", value))
            assert(var0 == value)
          }
        }
      }

      test("JVM option file") {
        unixOnly {
          val fileName  = "opts'\"$(touch pwned-file)`touch pwned-file2`\\$HOME"
          val (args, _) = runSh(
            Preamble().copy(jvmOptionFile = Some(fileName)),
            dir => os.write(dir / fileName, "-Dfrom=file\n")
          )
          assert(args.head == "-Dfrom=file")
        }
      }

      test("command") {
        unixOnly {
          val tmpDir = os.temp.dir(prefix = "cs-preamble-quoting")
          try {
            val dir = tmpDir / "a'\"$(touch pwned)`touch pwned2`\\$HOME"
            os.write(
              dir / "app",
              """#!/bin/sh
                |printf '%s\0' "$@" > "$CS_TEST_OUT/args"
                |: > "$CS_TEST_OUT/var"
                |""".stripMargin,
              perms = "rwxr-xr-x",
              createFolders = true
            )
            val (args, _) = runSh(
              Preamble().copy(command = Some(Preamble.shDoubleQuoteEscape((dir / "app").toString)))
            )
            assert(args == Seq("app-arg"))
          }
          finally os.remove.all(tmpDir)
        }
      }

      test("unchanged when no escaping is needed") {
        val content = new String(
          Preamble()
            .copy(javaOpts = Seq("-Xmx1g", "-Dfoo=bar baz"), jvmOptionFile = Some(".jvmopts"))
            .addExtraEnvVar("FOO", "bar baz")
            .sh,
          StandardCharsets.UTF_8
        )
        assert(content.contains("export FOO=\"bar baz\"\n"))
        assert(content.contains("exec \"$JAVA_CMD\" '-Xmx1g' '-Dfoo=bar baz' ${extra_jvm_opts[@]}"))
        assert(content.contains("jvm_opts_file=\".jvmopts\"\n"))
      }

      test("NUL is rejected") {
        assertThrows[IllegalArgumentException] {
          Preamble().copy(javaOpts = Seq("a\u0000b")).sh
        }
      }

      test("invalid env var names are rejected") {
        assertThrows[IllegalArgumentException] {
          Preamble().addExtraEnvVar("A=$(touch pwned)", "b").sh
        }
      }
    }

    test("bat") {
      def bat(preamble: Preamble): String =
        new String(preamble.bat, StandardCharsets.UTF_8)

      test("java options") {
        val content = bat(
          Preamble().copy(javaOpts =
            Seq(
              "-Xmx1g",
              "-Dfoo=a&calc.exe",
              "-Dpercent=%PATH%",
              "-Dspace=a b",
              "-Dcaret=^|<>",
              "-Dpath=C:\\a b\\"
            )
          )
        )
        assert(content.contains(
          "SET RUN_CMD=%JAVA_EXE% -Xmx1g \"-Dfoo=a&calc.exe\" -Dpercent=%%PATH%% \"-Dspace=a b\" " +
            "\"-Dcaret=^|<>\" \"-Dpath=C:\\a b\\\\\" %JAVA_OPTS%"
        ))
      }

      test("env vars") {
        val content = bat(
          Preamble()
            .addExtraEnvVar("A", "plain")
            .addExtraEnvVar("B", "x&calc.exe")
            .addExtraEnvVar("C", "100%")
        )
        assert(content.contains("set A=plain\r\nset \"B=x&calc.exe\"\r\nset C=100%%\r\n"))
      }

      test("command") {
        val content =
          bat(Preamble().copy(command =
            Some(Preamble.batDoubleQuoteEscape("C:\\a&b\\%x%\\app.exe"))
          ))
        assert(content.contains("SET RUN_CMD=\"C:\\a&b\\%%x%%\\app.exe\"\r\n"))
      }

      test("unrepresentable values are rejected") {
        for (value <- Seq("a\"&calc.exe", "a\r\ncalc.exe", "a\ncalc.exe", "a\u0000b")) {
          assertThrows[IllegalArgumentException] {
            Preamble().copy(javaOpts = Seq(value)).bat
          }
          assertThrows[IllegalArgumentException] {
            Preamble().addExtraEnvVar("A", value).bat
          }
          assertThrows[IllegalArgumentException] {
            Preamble.batDoubleQuoteEscape(value)
          }
        }
      }
    }
  }
}
