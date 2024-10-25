package coursier.clitests

import java.io._
import java.net.{ServerSocket, URI}
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.{Locale, UUID}
import java.util.regex.Pattern
import java.util.zip.ZipFile

import scala.concurrent.duration.Duration
import scala.io.{Codec, Source}
import scala.jdk.CollectionConverters._
import scala.util.Properties

import coursier.clitests.util.TestAuthProxy
import coursier.util.StringInterpolators._
import utest._

abstract class BootstrapTests extends TestSuite with LauncherOptions {

  def launcher: String
  def assembly: String

  def overrideProguarded: Option[Boolean] =
    None

  def enableNailgunTest: Boolean =
    true

  def hasDocker: Boolean =
    Properties.isLinux

  private val extraOptions =
    overrideProguarded match {
      case None        => Nil
      case Some(value) => Seq(s"--proguarded=$value")
    }

  val tests = Tests {
    test("simple") {
      TestUtil.withTempDir { tmpDir0 =>
        val tmpDir = os.Path(tmpDir0)
        os.proc(
          launcher,
          "bootstrap",
          "-o",
          "cs-echo",
          "io.get-coursier:echo:1.0.1",
          extraOptions
        ).call(cwd = tmpDir)
        val bootstrap =
          if (Properties.isWin) (tmpDir / "cs-echo.bat").toString
          else "./cs-echo"
        val output = os.proc(bootstrap, "foo")
          .call(cwd = tmpDir)
          .out.text()
        val expectedOutput = "foo" + System.lineSeparator()
        assert(output == expectedOutput)

        if (acceptsJOptions) {
          val outputWithJavaArgs =
            os.proc(bootstrap, "-J-Dother=thing", "foo", "-J-Dfoo=baz")
              .call(cwd = tmpDir)
              .out.text()
          assert(outputWithJavaArgs == expectedOutput)
        }

        val outputWithArgsWithSpace =
          os.proc(bootstrap, "-n foo")
            .call(cwd = tmpDir)
            .out.text()
        val expectedOutputWithArgsWithSpace = "-n foo" + System.lineSeparator()
        assert(outputWithArgsWithSpace == expectedOutputWithArgsWithSpace)
      }
    }

    def javaPropsTest(): Unit =
      TestUtil.withTempDir { tmpDir =>
        os.proc(
          launcher,
          "bootstrap",
          "-o",
          "cs-props",
          "--property",
          "other=thing",
          "--java-opt",
          "-Dfoo=baz",
          TestUtil.propsDepStr,
          "--jvm-option-file=.propsjvmopts",
          extraOptions
        ).call(cwd = os.Path(tmpDir))

        val fooOutput =
          os.proc("./cs-props", "foo")
            .call(cwd = os.Path(tmpDir))
            .out.text()
        val expectedFooOutput = "baz" + System.lineSeparator()
        assert(fooOutput == expectedFooOutput)

        val otherOutput = LauncherTestUtil.output(
          Seq("./cs-props", "other"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        val expectedOtherOutput = "thing" + System.lineSeparator()
        assert(otherOutput == expectedOtherOutput)

        if (acceptsJOptions) {
          val propArgOutput = LauncherTestUtil.output(
            Seq("./cs-props", "-J-Dhappy=days", "happy"),
            keepErrorOutput = false,
            directory = tmpDir
          )
          val expectedPropArgOutput = "days" + System.lineSeparator()
          assert(propArgOutput == expectedPropArgOutput)

          val optFile = new File(tmpDir, ".propsjvmopts")
          Files.write(
            optFile.toPath,
            ("-Dhappy=days" + System.lineSeparator()).getBytes(Charset.defaultCharset())
          )
          val optFileOutput = LauncherTestUtil.output(
            Seq("./cs-props", "happy"),
            keepErrorOutput = false,
            directory = tmpDir
          )
          Files.delete(optFile.toPath)
          val expectedOptFileOutput = "days" + System.lineSeparator()
          assert(optFileOutput == expectedOptFileOutput)
        }

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
      }
      else
        "disabled"
    }

    def javaPropsWithAssemblyTest(): Unit =
      TestUtil.withTempDir { tmpDir =>
        LauncherTestUtil.run(
          args = Seq(
            launcher,
            "bootstrap",
            "-a",
            "-o",
            "cs-props-assembly",
            "--property",
            "other=thing",
            "--java-opt",
            "-Dfoo=baz",
            TestUtil.propsDepStr
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
      }
      else
        "disabled"
    }

    test("java class path property") {
      TestUtil.withTempDir { tmpDir =>
        LauncherTestUtil.run(
          args =
            Seq(launcher, "bootstrap", "-o", "cs-props-0", TestUtil.propsDepStr) ++ extraOptions,
          directory = tmpDir
        )
        val output = LauncherTestUtil.output(
          Seq("./cs-props-0", "java.class.path"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        if (Properties.isWin) {
          val expectedOutput =
            (new File(tmpDir, "./cs-props-0").getCanonicalPath +: TestUtil.propsCp).mkString(
              File.pathSeparator
            ) + System.lineSeparator()
          assert(output.replace("\\\\", "\\") == expectedOutput)
        }
        else {
          val expectedOutput = ("./cs-props-0" +: TestUtil.propsCp).mkString(
            File.pathSeparator
          ) + System.lineSeparator()
          assert(output == expectedOutput)
        }
      }
    }

    test("java_class_path property in expansion") {
      TestUtil.withTempDir { tmpDir =>
        val pwd     = if (Properties.isWin) os.Path(os.pwd.toIO.getCanonicalFile) else os.pwd
        val tmpDir0 = os.Path(if (Properties.isWin) tmpDir.getCanonicalFile else tmpDir, pwd)
        os.proc(
          LauncherTestUtil.adaptCommandName(launcher, tmpDir),
          "bootstrap",
          "-o",
          "cs-props-1",
          "--property",
          "foo=${java.class.path}__${java.class.path}",
          TestUtil.propsDepStr,
          extraOptions
        ).call(cwd = tmpDir0)
        val output = os.proc(LauncherTestUtil.adaptCommandName("./cs-props-1", tmpDir), "foo")
          .call(cwd = tmpDir0)
          .out.text()
        if (Properties.isWin) {
          val outputElems =
            ((tmpDir0 / "cs-props-1").toString +: TestUtil.propsCp).mkString(File.pathSeparator)
          val expectedOutput = outputElems + "__" + outputElems + System.lineSeparator()
          assert(output.replace("\\\\", "\\") == expectedOutput)
        }
        else {
          val cp             = ("./cs-props-1" +: TestUtil.propsCp).mkString(File.pathSeparator)
          val expectedOutput = cp + "__" + cp + System.lineSeparator()
          assert(output == expectedOutput)
        }
      }
    }

    test("space in main jar path") {
      TestUtil.withTempDir { tmpDir =>
        Files.createDirectories(tmpDir.toPath.resolve("dir with space"))
        LauncherTestUtil.run(
          args = Seq(
            launcher,
            "bootstrap",
            "-o",
            "dir with space/cs-props-0",
            TestUtil.propsDepStr
          ) ++ extraOptions,
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
          args = Seq(
            launcher,
            "bootstrap",
            "-o",
            "cs-echo-mf",
            "io.get-coursier:echo:1.0.1",
            "--manifest-jar"
          ) ++ extraOptions,
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
          args = Seq(
            launcher,
            "bootstrap",
            "-o",
            "cs-echo-hybrid",
            "io.get-coursier:echo:1.0.1",
            "--hybrid"
          ) ++ extraOptions,
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
          args = Seq(
            launcher,
            "bootstrap",
            "-o",
            "cs-props-hybrid",
            TestUtil.propsDepStr,
            "--hybrid"
          ) ++ extraOptions,
          directory = tmpDir
        )
        val output = LauncherTestUtil.output(
          Seq("./cs-props-hybrid", "java.class.path"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        if (Properties.isWin) {
          val expectedOutput =
            new File(tmpDir, "./cs-props-hybrid").getCanonicalPath + System.lineSeparator()
          assert(output.replace("\\\\", "\\") == expectedOutput)
        }
        else {
          val expectedOutput = "./cs-props-hybrid" + System.lineSeparator()
          assert(output == expectedOutput)
        }
      }
    }

    test("hybrid with shared dep java class path") {
      TestUtil.withTempDir { tmpDir =>
        val tmpDir0 = os.Path(tmpDir, os.pwd)
        os.proc(
          LauncherTestUtil.adaptCommandName(launcher, tmpDir),
          "bootstrap",
          "-o",
          "cs-props-hybrid-shared",
          TestUtil.propsDepStr,
          "io.get-coursier:echo:1.0.2",
          "--shared",
          "io.get-coursier:echo",
          "--hybrid",
          extraOptions
        ).call(cwd = tmpDir0)

        val zf = new ZipFile((tmpDir0 / "cs-props-hybrid-shared").toIO)
        val nativeImageEntries = zf.entries()
          .asScala
          .filter(_.getName.startsWith("META-INF/native-image/"))
          .toVector
        zf.close()
        assert(nativeImageEntries.isEmpty)

        val output = os.proc(
          LauncherTestUtil.adaptCommandName("./cs-props-hybrid-shared", tmpDir),
          "java.class.path"
        )
          .call(cwd = tmpDir0)
          .out.text(Codec.default)
        if (Properties.isWin) {
          val expectedOutput =
            new File(tmpDir, "./cs-props-hybrid-shared").getCanonicalPath + System.lineSeparator()
          assert(output.replace("\\\\", "\\") == expectedOutput)
        }
        else {
          val expectedOutput = "./cs-props-hybrid-shared" + System.lineSeparator()
          assert(output == expectedOutput)
        }
      }
    }

    test("hybrid with rules") {
      TestUtil.withTempDir { tmpDir =>
        val tmpDir0 = os.Path(tmpDir, os.pwd)

        def generate(output: String, extraArgs: String*): Unit =
          os.proc(
            LauncherTestUtil.adaptCommandName(launcher, tmpDir),
            "bootstrap",
            "-o",
            output,
            TestUtil.propsDepStr,
            "io.get-coursier:echo:1.0.2",
            "--shared",
            "io.get-coursier:echo",
            "--hybrid",
            extraArgs,
            extraOptions
          ).call(cwd = tmpDir0)

        generate("base")
        generate("with-rule", "-R", "exclude:coursier/echo/Echo.class")

        def hasEchoEntry(output: String): Boolean = {
          val zf = new ZipFile((tmpDir0 / output).toIO)
          try
            zf.entries()
              .asScala
              .exists(_.getName == "coursier/echo/Echo.class")
          finally
            zf.close()
        }

        assert(hasEchoEntry("base"))
        assert(!hasEchoEntry("with-rule"))
      }
    }

    test("standalone") {
      TestUtil.withTempDir { tmpDir =>
        LauncherTestUtil.run(
          args = Seq(
            launcher,
            "bootstrap",
            "-o",
            "cs-echo-standalone",
            "io.get-coursier:echo:1.0.1",
            "--standalone"
          ) ++ extraOptions,
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
            "-o",
            "cs-scalafmt-standalone",
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
            "--intransitive",
            "io.get-coursier::coursier-cli:1.1.0-M14-2",
            "-o",
            "coursier-test.jar",
            "--assembly",
            "--classifier",
            "standalone",
            "-A",
            "jar"
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
              "-o",
              "echo-ng",
              "--standalone",
              "io.get-coursier:echo:1.0.0",
              "com.facebook:nailgun-server:1.0.0",
              "-M",
              "com.facebook.nailgun.NGServer"
            ) ++ extraOptions,
            directory = tmpDir
          )
          var bgProc: Process = null
          val output =
            try {
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
            }
            finally if (bgProc != null)
                bgProc.destroy()

          val expectedOutput = "foo" + System.lineSeparator()
          assert(output == expectedOutput)
        }
    }

    test("python jep") {
      TestUtil.withTempDir { tmpDir =>
        LauncherTestUtil.run(
          args = Seq(
            launcher,
            "bootstrap",
            "-o",
            "props-python",
            "--python-jep",
            TestUtil.propsDepStr
          ) ++ extraOptions,
          directory = tmpDir
        )

        val jnaLibraryPath = LauncherTestUtil.output(
          Seq("./props-python", "jna.library.path"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        assert(jnaLibraryPath.trim.nonEmpty)

        val jnaNoSys = LauncherTestUtil.output(
          Seq("./props-python", "jna.nosys"),
          keepErrorOutput = false,
          directory = tmpDir
        )
        assert(jnaNoSys.trim == "false")
      }
    }

    test("python") {
      TestUtil.withTempDir { tmpDir0 =>
        val tmpDir = os.Path(tmpDir0)
        os.proc(
          launcher,
          "bootstrap",
          "-o",
          "echo-scalapy",
          "--python",
          "io.get-coursier:scalapy-echo_2.13:1.0.7",
          extraOptions
        ).call(cwd = tmpDir, stdin = os.Inherit, stdout = os.Inherit)
        os.proc(
          launcher,
          "bootstrap",
          "-o",
          "echo-scalapy-no-python",
          "io.get-coursier:scalapy-echo_2.13:1.0.7",
          extraOptions
        ).call(cwd = tmpDir, stdin = os.Inherit, stdout = os.Inherit)

        def bootstrap(name: String): String =
          if (Properties.isWin) (tmpDir / s"$name.bat").toString
          else s"./$name"
        val res = os.proc(bootstrap("echo-scalapy"), "a", "b").call(cwd = tmpDir)
        assert(res.out.trim() == "a b")

        // Commented out, as things can work without Python-specific setup in some
        // environments.
        // val noPythonRes = os.proc(bootstrap("echo-scalapy-no-python"), "a", "b").call(
        //   cwd = tmpDir,
        //   mergeErrIntoOut = true,
        //   check = false
        // )
        // System.err.write(noPythonRes.out.bytes)
        // assert(noPythonRes.exitCode != 0)
      }
    }

    test("python native") {
      // both false means native launcher, where we can't use 'cs bootstrap --native'
      // (as it class loads a launcher-native module at runtime)
      if (acceptsDOptions || acceptsJOptions)
        pythonNativeTest()
    }
    def pythonNativeTest(): Unit = {
      TestUtil.withTempDir { tmpDir =>
        os.proc(
          launcher,
          "bootstrap",
          "-o",
          "echo-scalapy-native",
          "--python",
          "--native",
          "io.get-coursier:scalapy-echo_native0.4_2.13:1.0.7",
          extraOptions
        ).call(cwd = os.Path(tmpDir), stdin = os.Inherit, stdout = os.Inherit)

        val res = os.proc("./echo-scalapy-native", "a", "b").call(cwd = os.Path(tmpDir))
        assert(res.out.trim() == "a b")
      }
    }

    test("authenticated proxy") {
      if (hasDocker) authenticatedProxyTest()
      else "Docker test disabled"
    }

    def authenticatedProxyTest(): Unit = {

      TestUtil.withTempDir { tmpDir0 =>
        val tmpDir = os.Path(tmpDir0, os.pwd)

        os.proc(launcher, "bootstrap", "-o", "cs-echo", "io.get-coursier:echo:1.0.1", extraOptions)
          .call(cwd = tmpDir)

        val output = os.proc("./cs-echo", "foo")
          .call(cwd = tmpDir)
          .out.text()
        val expectedOutput = "foo" + System.lineSeparator()
        assert(output == expectedOutput)

        val okM2Dir = tmpDir / "m2-ok"
        os.write(okM2Dir / "settings.xml", TestAuthProxy.m2Settings(), createFolders = true)
        val nopeM2Dir = tmpDir / "m2-nope"
        os.write(
          nopeM2Dir / "settings.xml",
          TestAuthProxy.m2Settings(9083, "wrong", "nope"),
          createFolders = true
        )

        TestAuthProxy.withAuthProxy { _ =>

          val proc = os.proc("./cs-echo", "foo")

          val failCheck = proc
            .call(
              cwd = tmpDir,
              env = Map(
                "COURSIER_CACHE" -> (tmpDir / "cache-1").toString,
                "CS_MAVEN_HOME"  -> nopeM2Dir.toString
              ),
              check = false,
              mergeErrIntoOut = true
            )

          assert(failCheck.exitCode != 0)
          assert(failCheck.out.text().contains("407 Proxy Authentication Required"))

          val output = proc
            .call(
              cwd = tmpDir,
              env = Map(
                "COURSIER_CACHE" -> (tmpDir / "cache-1").toString,
                "CS_MAVEN_HOME"  -> okM2Dir.toString
              )
            )
            .out.text()
          val expectedOutput = "foo" + System.lineSeparator()
          assert(output == expectedOutput)
        }
      }
    }

    test("config file authenticated proxy") {
      if (hasDocker) configFileAuthenticatedProxyTest()
      else "Docker test disabled"
    }

    def withAuthProxy[T](f: (String, String, Int) => T): T = {
      val networkName = "cs-test-" + UUID.randomUUID().toString
      var containerId = ""
      try {
        os.proc("docker", "network", "create", networkName)
          .call(stdin = os.Inherit, stdout = os.Inherit)
        val host = {
          val res  = os.proc("docker", "network", "inspect", networkName).call(stdin = os.Inherit)
          val resp = ujson.read(res.out.trim())
          resp.arr(0).apply("IPAM").apply("Config").arr(0).apply("Gateway").str
        }
        val port = {
          val s = new ServerSocket(0)
          try s.getLocalPort
          finally s.close()
        }
        val res = os.proc(
          "docker",
          "run",
          "-d",
          "--rm",
          "-p",
          s"$port:80",
          "--network",
          networkName,
          "bahamat/authenticated-proxy@sha256:568c759ac687f93d606866fbb397f39fe1350187b95e648376b971e9d7596e75"
        )
          .call(stdin = os.Inherit)
        containerId = res.out.trim()
        f(networkName, host, port)
      }
      finally {
        if (containerId.nonEmpty) {
          System.err.println(s"Removing container $containerId")
          os.proc("docker", "rm", "-f", containerId)
            .call(stdin = os.Inherit, stdout = os.Inherit)
        }
        os.proc("docker", "network", "rm", networkName)
          .call(stdin = os.Inherit, stdout = os.Inherit)
      }
    }

    def configFileAuthenticatedProxyTest(): Unit =
      TestUtil.withTempDir { tmpDir0 =>
        val tmpDir = os.Path(tmpDir0, os.pwd)

        os.proc(launcher, "bootstrap", "-o", "cs-echo", "io.get-coursier:echo:1.0.1", extraOptions)
          .call(cwd = tmpDir)

        val output = os.proc("./cs-echo", "foo")
          .call(cwd = tmpDir)
          .out.text()
        val expectedOutput = "foo" + System.lineSeparator()
        assert(output == expectedOutput)

        withAuthProxy { (networkName, proxyHost, proxyPort) =>
          val configContent =
            s"""{
               |  "httpProxy": {
               |    "address": "http://$proxyHost:$proxyPort",
               |    "user": "value:jack",
               |    "password": "value:insecure"
               |  }
               |}
               |""".stripMargin
          val configFile = tmpDir / "config.json"
          os.write(configFile, configContent)

          val header = "*** Running command ***"

          val words = Seq("a", "b", "foo")

          val scriptContent =
            s"""#!/usr/bin/env bash
               |set -e
               |export PATH="$$(pwd)/bin:$$PATH"
               |if ./cs-echo a b foo; then
               |  echo "Expected command to fail at first"
               |  exit 1
               |fi
               |export SCALA_CLI_CONFIG="$$(pwd)/config.json"
               |echo "$header"
               |exec ./cs-echo ${words.map(w => "\"" + w + "\"").mkString(" ")}
               |""".stripMargin
          os.write(tmpDir / "script.sh", scriptContent)

          os.copy(
            os.Path(assembly, os.pwd),
            tmpDir / "bin" / "cs",
            createFolders = true,
            copyAttributes = true
          )

          val res = os.proc(
            "docker",
            "run",
            "--rm",
            "--dns",
            "0.0.0.0",
            "--network",
            networkName,
            "-v",
            s"$tmpDir:/data",
            "-w",
            "/data",
            "eclipse-temurin:17",
            "/bin/bash",
            "./script.sh"
          )
            .call(cwd = tmpDir)
          val output = res.out.text().split(Pattern.quote(header)).last
          assert(output.trim() == words.mkString(" "))
        }
      }

    test("mirror") {
      TestUtil.withTempDir { tmpDir0 =>
        val tmpDir = os.Path(tmpDir0, os.pwd)
        val cache  = tmpDir / "cache"

        val propsLauncher = tmpDir / "props"
        os.proc(
          launcher,
          "bootstrap",
          "io.get-coursier:props:1.0.7",
          "--cache",
          cache,
          "-o",
          propsLauncher
        )
          .call(stdin = os.Inherit, stdout = os.Inherit)

        val urls0 = {
          val zf = new ZipFile(propsLauncher.toIO)
          val e  = zf.getEntry("coursier/bootstrap/launcher/bootstrap-jar-urls")
          val is = zf.getInputStream(e)
          try Source.fromInputStream(is)(Codec.UTF8).mkString.linesIterator.toVector
          finally is.close()
        }

        assert(urls0.nonEmpty)
        assert(urls0.forall(_.startsWith("https://repo1.maven.org/maven2/")))

        val configContent =
          s"""{
             |  "repositories": {
             |    "mirrors": [
             |      "https://maven-central.storage-download.googleapis.com/maven2 = https://repo1.maven.org/maven2"
             |    ]
             |  }
             |}
             |""".stripMargin
        val configFile = tmpDir / "config.json"
        os.write(configFile, configContent)

        val binDir = tmpDir / "bin"
        val ext    = if (Properties.isWin) ".bat" else ""
        os.copy(
          os.Path(assembly, os.pwd),
          binDir / s"cs$ext",
          createFolders = true,
          copyAttributes = true
        )

        val (pathVarName, pathValue) = sys.env
          .find(_._1.toLowerCase(java.util.Locale.ROOT) == "path")
          .getOrElse(("PATH", ""))
        val fullPath =
          Seq(binDir.toString, pathValue).mkString(File.pathSeparator)
        val propsLauncher0 =
          if (Properties.isWin) propsLauncher / os.up / (propsLauncher.last + ".bat")
          else propsLauncher
        val res1 = os.proc(propsLauncher0, "java.class.path")
          .call(
            env = Map(
              "SCALA_CLI_CONFIG" -> configFile.toString,
              "COURSIER_CACHE"   -> cache.toString,
              pathVarName        -> fullPath
            )
          )
        val jars1 = res1.out.trim()
          .split(File.pathSeparator)
          .toVector
          .map(os.Path(_, os.pwd))
          .filter(_ != propsLauncher)
          .map(_.relativeTo(cache))

        val gcsPrefix =
          os.rel / "https" / "maven-central.storage-download.googleapis.com" / "maven2"
        assert(jars1.forall(_.startsWith(gcsPrefix)))
      }
    }

    test("credentials from properties") {
      credentialsTest(useConfig = false)
    }
    test("credentials from config") {
      credentialsTest(useConfig = true)
    }

    def credentialsTest(useConfig: Boolean): Unit = {
      val user     = "alex"
      val password = "secure1234"
      val realm    = "therealm"
      val host     = "127.0.0.1"
      val port = {
        val s = new ServerSocket(0)
        try s.getLocalPort()
        finally s.close()
      }
      TestUtil.withTempDir("credentialstest") { root0 =>
        val root = os.Path(root0)

        val credentialsEnv =
          if (useConfig) {

            val binDir = root / "bin"
            val ext    = if (Properties.isWin) ".bat" else ""
            os.copy(
              os.Path(assembly, os.pwd),
              binDir / s"cs$ext",
              createFolders = true,
              copyAttributes = true
            )

            val (pathKey, currentPath) =
              sys.env.find(_._1.toLowerCase(Locale.ROOT) == "path").getOrElse(("PATH", ""))
            val fullPath =
              Seq(binDir.toString, currentPath).mkString(File.pathSeparator)

            val configContent =
              s"""{
                 |  "repositories": {
                 |    "credentials": [
                 |      {
                 |        "host": "$host",
                 |        "user": "value:$user",
                 |        "password": "value:$password",
                 |        "realm": "$realm",
                 |        "httpsOnly": false,
                 |        "matchHost": true
                 |      }
                 |    ]
                 |  }
                 |}
                 |""".stripMargin
            val configFile = root / "credentials.json"
            os.write(configFile, configContent)
            Map("SCALA_CLI_CONFIG" -> configFile.toString, pathKey -> fullPath)
          }
          else {
            val propertiesContent =
              s"""simple.username=$user
                 |simple.password=$password
                 |simple.host=$host
                 |simple.realm=$realm
                 |simple.https-only=false
                 |simple.auto=true
                 |""".stripMargin
            val propertiesFile = root / "credentials.properties"
            os.write(propertiesFile, propertiesContent)
            Map("COURSIER_CREDENTIALS" -> propertiesFile.toNIO.toUri.toString)
          }

        val propsDep    = "io.get-coursier:props:1.0.7"
        val firstCache  = root / "cache"
        val secondCache = root / "cache-2"
        val thirdCache  = root / "cache-3"
        os.proc(launcher, "fetch", propsDep, "--cache", firstCache)
          .call(cwd = root, stdin = os.Inherit, stdout = os.Inherit)
        val proc = os.proc(
          launcher,
          "launch",
          "io.get-coursier:http-server_2.12:1.0.1",
          "--",
          "--user",
          user,
          "--password",
          password,
          "--realm",
          realm,
          "--directory",
          firstCache / "https" / "repo1.maven.org" / "maven2",
          "--host",
          host,
          "--port",
          port,
          "-v"
        )
          .spawn(cwd = root, mergeErrIntoOut = true)
        try {

          // a timeout around this would be great…
          System.err.println(s"Waiting for local HTTP server to get started on $host:$port…")
          var lineOpt = Option.empty[String]
          while (
            proc.isAlive() && {
              lineOpt = Some(proc.stdout.readLine())
              !lineOpt.exists(_.startsWith("Listening on "))
            }
          )
            for (l <- lineOpt)
              System.err.println(l)

          // Seems required, especially when using native launchers
          val waitFor = Duration(2L, "s")
          System.err.println(s"Waiting $waitFor")
          Thread.sleep(waitFor.toMillis)

          val t = new Thread("test-http-server-output") {
            setDaemon(true)
            override def run(): Unit = {
              var line = ""
              while (
                proc.isAlive() && {
                  line = proc.stdout.readLine()
                  line != null
                }
              )
                System.err.println(line)
            }
          }
          t.start()

          val propsLauncher = root / "props"
          os.proc(
            launcher,
            "bootstrap",
            "--no-default",
            "-r",
            s"http://$host:$port",
            propsDep,
            "-o",
            propsLauncher,
            "--cache",
            secondCache
          )
            .call(cwd = root, env = credentialsEnv)

          val propsLauncher0 =
            if (Properties.isWin) propsLauncher / os.up / s"${propsLauncher.last}.bat"
            else propsLauncher
          val res = os.proc(propsLauncher0, "java.class.path")
            .call(
              cwd = root,
              env = Map("COURSIER_CACHE" -> thirdCache.toString) ++ credentialsEnv
            )
          val propsBootstrapCp =
            res.out.trim().split(File.pathSeparator).toVector.map(os.Path(_, root))
          val actualCp = propsBootstrapCp.filter(_ != propsLauncher)
          assert(actualCp.nonEmpty)
          assert(actualCp.forall(_.startsWith(thirdCache)))
        }
        finally {
          proc.destroy()
          Thread.sleep(100L)
          if (proc.isAlive()) {
            Thread.sleep(1000L)
            proc.destroy(shutdownGracePeriod = 0)
          }
        }
      }
    }

    test("hybrid with coursier dependency") {
      TestUtil.withTempDir("hybrid-cs") { tmpDir =>
        os.proc(
          launcher,
          "bootstrap",
          "--hybrid",
          "sh.almond:::scala-kernel:0.13.6",
          "--shared",
          "sh.almond:::scala-kernel-api",
          "-r",
          "jitpack",
          "--scala",
          "2.12.17",
          "-o",
          "almond212"
        ).call(cwd = os.Path(tmpDir, os.pwd))
      }
    }

    test("jni-utils from bootstrap") {
      if (Properties.isWin)
        jniUtilFromBootstrapTest()
      else
        "disabled"
    }

    test("jni-utils from hybrid bootstrap") {
      if (Properties.isWin)
        jniUtilFromBootstrapTest("--hybrid")
      else
        "disabled"
    }

    test("jni-utils from standalone bootstrap") {
      if (Properties.isWin)
        jniUtilFromBootstrapTest("--standalone")
      else
        "disabled"
    }

    def jniUtilFromBootstrapTest(extraOpts: String*): Unit = {
      TestUtil.withTempDir("jni-cs") { tmpDir0 =>
        val tmpDir = os.Path(tmpDir0, os.pwd)

        val repo        = tmpDir / "repo"
        val appLauncher = tmpDir / "app"
        val actualAppLauncher =
          if (Properties.isWin) tmpDir / "app.bat"
          else appLauncher

        val appSource = tmpDir / "TestApp.scala"
        os.write(
          appSource,
          """//> using scala "2.13.10"
            |//> using lib "io.get-coursier.jniutils:windows-jni-utils:0.3.3"
            |//> using publish.organization "io.get-coursier.tests"
            |//> using publish.name "test-app"
            |//> using publish.version "0.1.0"
            |
            |package testapp
            |
            |import coursier.jniutils.WindowsEnvironmentVariables
            |
            |object TestApp {
            |  def main(args: Array[String]): Unit = {
            |    val path = WindowsEnvironmentVariables.get("PATH")
            |    System.err.println(s"PATH=$path")
            |  }
            |}
            |""".stripMargin
        )

        os.proc(
          TestUtil.scalaCli,
          "--power",
          "publish",
          "--server=false",
          "--publish-repo",
          repo.toNIO.toUri.toASCIIString,
          appSource
        )
          .call(cwd = tmpDir, stdin = os.Inherit, stdout = os.Inherit)

        os.proc(
          launcher,
          "bootstrap",
          "-r",
          repo.toNIO.toUri.toASCIIString,
          "io.get-coursier.tests::test-app:0.1.0",
          "--scala",
          "2.13.10",
          "-o",
          appLauncher,
          extraOpts
        ).call(cwd = tmpDir, stdin = os.Inherit, stdout = os.Inherit)

        os.proc(actualAppLauncher)
          .call(cwd = tmpDir, stdin = os.Inherit, stdout = os.Inherit)

        os.proc("java", "-Dcoursier.jni.check.throw=true", "-jar", appLauncher)
          .call(cwd = tmpDir, stdin = os.Inherit, stdout = os.Inherit)
      }
    }
  }
}
