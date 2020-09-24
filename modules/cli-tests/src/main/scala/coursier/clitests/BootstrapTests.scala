package coursier.clitests

import java.io._
import java.nio.charset.Charset
import java.nio.file.Files

import coursier.dependencyString
import utest._

abstract class BootstrapTests extends TestSuite {

  def launcher: String
  def acceptsDOptions: Boolean = true

  def overrideProguarded: Option[Boolean] =
    None

  def enableNailgunTest: Boolean =
    true

  private val extraOptions =
    overrideProguarded match {
      case None => Nil
      case Some(value) => Seq(s"--proguarded=$value")
    }

  val tests = Tests {
    test("simple") {
      TestUtil.withTempDir { tmpDir =>
        LauncherTestUtil.run(
          args = Seq(launcher, "bootstrap", "-o", "cs-echo", "io.get-coursier:echo:1.0.1") ++ extraOptions,
          directory = tmpDir
        )
        val output = LauncherTestUtil.output(
          Seq("./cs-echo", "foo"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        val expectedOutput = "foo" + System.lineSeparator()
        assert(output == expectedOutput)

        val outputWithJavaArgs = LauncherTestUtil.output(
          Seq("./cs-echo", "-J-Dother=thing", "foo", "-J-Dfoo=baz"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        assert(outputWithJavaArgs == expectedOutput)

        val outputWithArgsWithSpace = LauncherTestUtil.output(
          Seq("./cs-echo", "-n foo"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        val expectedOutputWithArgsWithSpace = "-n foo" + System.lineSeparator()
        assert(outputWithArgsWithSpace == expectedOutputWithArgsWithSpace)
      }
    }

    def javaPropsTest(): Unit =
      TestUtil.withTempDir { tmpDir =>
        LauncherTestUtil.run(
          args = Seq(
            launcher,
            "bootstrap",
            "-o", "cs-props",
            "--property", "other=thing",
            "--java-opt", "-Dfoo=baz",
            "io.get-coursier:props:1.0.2",
            "--jvm-option-file=.propsjvmopts"
          ) ++ extraOptions,
          directory = tmpDir
        )

        val fooOutput = LauncherTestUtil.output(
          Seq("./cs-props", "foo"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        val expectedFooOutput = "baz" + System.lineSeparator()
        assert(fooOutput == expectedFooOutput)

        val otherOutput = LauncherTestUtil.output(
          Seq("./cs-props", "other"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        val expectedOtherOutput = "thing" + System.lineSeparator()
        assert(otherOutput == expectedOtherOutput)

        val propArgOutput = LauncherTestUtil.output(
          Seq("./cs-props", "-J-Dhappy=days", "happy"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        val expectedPropArgOutput = "days" + System.lineSeparator()
        assert(propArgOutput == expectedPropArgOutput)

        val optFile = new File(tmpDir, ".propsjvmopts")
        Files.write(optFile.toPath, ("-Dhappy=days" + System.lineSeparator()).getBytes(Charset.defaultCharset()))
        val optFileOutput = LauncherTestUtil.output(
          Seq("./cs-props", "happy"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        Files.delete(optFile.toPath)
        val expectedOptFileOutput = "days" + System.lineSeparator()
        assert(optFileOutput == expectedOptFileOutput)

        val javaOptsOutput = LauncherTestUtil.output(
          Seq("./cs-props", "happy"),
          keepErrorOutput = false,
          directory = tmpDir,
          extraEnv = Map("JAVA_OPTS" -> "-Dhappy=days")
        )
        val expectedJavaOptsOutput = "days" + System.lineSeparator()
        assert(javaOptsOutput == expectedJavaOptsOutput)

        val multiJavaOptsOutput = LauncherTestUtil.output(
          Seq("./cs-props", "happy"),
          keepErrorOutput = false,
          directory = tmpDir,
          extraEnv = Map("JAVA_OPTS" -> "-Dhappy=days -Dfoo=other")
        )
        val expectedMultiJavaOptsOutput = "days" + System.lineSeparator()
        assert(multiJavaOptsOutput == expectedMultiJavaOptsOutput)
      }
    test("java props") {
      if (acceptsDOptions) {
        javaPropsTest()
        "ok"
      } else
        "disabled"
    }

    def javaPropsWithAssemblyTest(): Unit =
      TestUtil.withTempDir { tmpDir =>
        LauncherTestUtil.run(
          args = Seq(
            launcher,
            "bootstrap",
            "-a",
            "-o", "cs-props-assembly",
            "--property", "other=thing",
            "--java-opt", "-Dfoo=baz",
            "io.get-coursier:props:1.0.2"
          ) ++ extraOptions,
          directory = tmpDir
        )

        val fooOutput = LauncherTestUtil.output(
          Seq("./cs-props-assembly", "foo"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        val expectedFooOutput = "baz" + System.lineSeparator()
        assert(fooOutput == expectedFooOutput)

        val otherOutput = LauncherTestUtil.output(
          Seq("./cs-props-assembly", "other"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        val expectedOtherOutput = "thing" + System.lineSeparator()
        assert(otherOutput == expectedOtherOutput)

        // FIXME Test more stuff like cs-props above?
      }
    test("java props via assembly") {
      if (acceptsDOptions) {
        javaPropsWithAssemblyTest()
        "ok"
      } else
        "disabled"
    }

    test("java.class.path property") {
      TestUtil.withTempDir { tmpDir =>
        LauncherTestUtil.run(
          args = Seq(launcher, "bootstrap", "-o", "cs-props-0", TestUtil.propsDepStr) ++ extraOptions,
          directory = tmpDir
        )
        val output = LauncherTestUtil.output(
          Seq("./cs-props-0", "java.class.path"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        val expectedOutput = ("./cs-props-0" +: TestUtil.propsCp).mkString(File.pathSeparator) + System.lineSeparator()
        assert(output == expectedOutput)
      }
    }

    test("java.class.path property in expansion") {
      TestUtil.withTempDir { tmpDir =>
        LauncherTestUtil.run(
          args = Seq(launcher, "bootstrap", "-o", "cs-props-1", "--property", "foo=${java.class.path}", TestUtil.propsDepStr) ++ extraOptions,
          directory = tmpDir
        )
        val output = LauncherTestUtil.output(
          Seq("./cs-props-1", "foo"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        val expectedOutput = ("./cs-props-1" +: TestUtil.propsCp).mkString(File.pathSeparator) + System.lineSeparator()
        assert(output == expectedOutput)
      }
    }

    test("space in main jar path") {
      TestUtil.withTempDir { tmpDir =>
        Files.createDirectories(tmpDir.toPath.resolve("dir with space"))
        LauncherTestUtil.run(
          args = Seq(launcher, "bootstrap", "-o", "dir with space/cs-props-0", TestUtil.propsDepStr) ++ extraOptions,
          directory = tmpDir
        )
        val output = LauncherTestUtil.output(
          Seq("./dir with space/cs-props-0", "coursier.mainJar"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        val expectedInOutput = "dir with space"
        assert(output.contains(expectedInOutput))
      }
    }

    test("manifest jar") {
      TestUtil.withTempDir { tmpDir =>
        LauncherTestUtil.run(
          args = Seq(launcher, "bootstrap", "-o", "cs-echo-mf", "io.get-coursier:echo:1.0.1", "--manifest-jar") ++ extraOptions,
          directory = tmpDir
        )
        val output = LauncherTestUtil.output(
          Seq("./cs-echo-mf", "foo"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        val expectedOutput = "foo" + System.lineSeparator()
        assert(output == expectedOutput)
      }
    }

    test("hybrid") {
      TestUtil.withTempDir { tmpDir =>
        LauncherTestUtil.run(
          args = Seq(launcher, "bootstrap", "-o", "cs-echo-hybrid", "io.get-coursier:echo:1.0.1", "--hybrid") ++ extraOptions,
          directory = tmpDir
        )
        val output = LauncherTestUtil.output(
          Seq("./cs-echo-hybrid", "foo"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        val expectedOutput = "foo" + System.lineSeparator()
        assert(output == expectedOutput)
      }
    }

    test("hybrid java.class.path") {
      TestUtil.withTempDir { tmpDir =>
        LauncherTestUtil.run(
          args = Seq(launcher, "bootstrap", "-o", "cs-props-hybrid", TestUtil.propsDepStr, "--hybrid") ++ extraOptions,
          directory = tmpDir
        )
        val output = LauncherTestUtil.output(
          Seq("./cs-props-hybrid", "java.class.path"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        val expectedOutput = "./cs-props-hybrid" + System.lineSeparator()
        assert(output == expectedOutput)
      }
    }

    test("hybrid with shared dep java.class.path") {
      TestUtil.withTempDir { tmpDir =>
        LauncherTestUtil.run(
          args = Seq(
            launcher,
            "bootstrap",
            "-o", "cs-props-hybrid-shared",
            TestUtil.propsDepStr,
            "io.get-coursier:echo:1.0.2",
            "--shared", "io.get-coursier:echo",
            "--hybrid"
          ) ++ extraOptions,
          directory = tmpDir
        )
        val output = LauncherTestUtil.output(
          Seq("./cs-props-hybrid-shared", "java.class.path"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        val expectedOutput = "./cs-props-hybrid-shared" + System.lineSeparator()
        assert(output == expectedOutput)
      }
    }

    test("standalone") {
      TestUtil.withTempDir { tmpDir =>
        LauncherTestUtil.run(
          args = Seq(launcher, "bootstrap", "-o", "cs-echo-standalone", "io.get-coursier:echo:1.0.1", "--standalone") ++ extraOptions,
          directory = tmpDir
        )
        val output = LauncherTestUtil.output(
          Seq("./cs-echo-standalone", "foo"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        val expectedOutput = "foo" + System.lineSeparator()
        assert(output == expectedOutput)
      }
    }

    test("scalafmt standalone") {
      TestUtil.withTempDir { tmpDir =>
        LauncherTestUtil.run(
          args = Seq(
            launcher,
            "bootstrap",
            "-o", "cs-scalafmt-standalone",
            "org.scalameta:scalafmt-cli_2.12:2.0.0-RC4",
            "--standalone"
          ) ++ extraOptions,
          directory = tmpDir
        )
        LauncherTestUtil.run(
          Seq("./cs-scalafmt-standalone", "--help"),
          directory = tmpDir
        )
      }
    }

    test("jar with bash preamble") {
      // source jar here has a bash preamble, which assembly should ignore
      TestUtil.withTempDir { tmpDir =>
        LauncherTestUtil.run(
          args = Seq(
            launcher,
            "bootstrap",
            "--intransitive", "io.get-coursier::coursier-cli:1.1.0-M14-2",
            "-o", "coursier-test.jar",
            "--assembly",
            "--classifier", "standalone",
            "-A", "jar"
          ) ++ extraOptions,
          directory = tmpDir
        )
        LauncherTestUtil.run(
          Seq("./coursier-test.jar", "--help"),
          directory = tmpDir
        )
      }
    }

    test("nailgun") {
      if (enableNailgunTest)
        TestUtil.withTempDir { tmpDir =>
          LauncherTestUtil.run(
            args = Seq(
              launcher,
              "bootstrap",
              "-o", "echo-ng",
              "--standalone",
              "io.get-coursier:echo:1.0.0",
              "com.facebook:nailgun-server:1.0.0",
              "-M", "com.facebook.nailgun.NGServer"
            ) ++ extraOptions,
            directory = tmpDir
          )
          var bgProc: Process = null
          val output = try {
            bgProc = new ProcessBuilder("java", "-jar", "./echo-ng")
              .directory(tmpDir)
              .inheritIO()
              .start()

            Thread.sleep(2000L)

            LauncherTestUtil.output(
              Seq(TestUtil.ngCommand, "coursier.echo.Echo", "foo"),
              keepErrorOutput = false,
              directory = tmpDir
            )
          } finally {
            if (bgProc != null)
              bgProc.destroy()
          }

          val expectedOutput = "foo" + System.lineSeparator()
          assert(output == expectedOutput)
        }
    }
  }
}
