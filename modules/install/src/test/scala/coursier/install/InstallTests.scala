package coursier.install

import java.io.{ByteArrayOutputStream, File}
import java.lang.ProcessBuilder.Redirect
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, FileSystemException, Path, Paths}
import java.time.Instant
import java.util.Locale
import java.util.zip.ZipFile

import coursier.cache.Cache
import coursier.cache.internal.FileUtil
import coursier.install.error.NotAnApplication
import coursier.launcher.Preamble
import coursier.launcher.internal.Windows
import coursier.testcache.TestCache
import coursier.util.Task
import utest._

import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal
import scala.util.{Properties, Using}

object InstallTests extends TestSuite {

  private def cache: Cache[Task] =
    TestCache.cache

  private def delete(d: Path): Unit =
    if (Files.isDirectory(d)) {
      var s: java.util.stream.Stream[Path] = null
      try {
        s = Files.list(d)
        s.iterator()
          .asScala
          .foreach(delete)
      }
      finally if (s != null)
          s.close()
    }
    else
      try Files.deleteIfExists(d)
      catch {
        case e: FileSystemException if Properties.isWin =>
          System.err.println(s"Ignored error while deleting temporary file $d: $e")
      }

  private def withTempDir[T](f: Path => T): T = {
    val tmpDir = Files.createTempDirectory("coursier-install-test")
    try f(tmpDir)
    finally delete(tmpDir)
  }

  private def withZipFile[T](f: File)(t: ZipFile => T): T = {
    var zf: ZipFile = null
    try {
      zf = new ZipFile(f)
      t(zf)
    }
    finally if (zf != null)
        zf.close()
  }

  private def assertHasNotEntry(f: File, path: String) =
    withZipFile(f) { zf =>
      val ent = zf.getEntry(path)
      Predef.assert(ent == null, s"Unexpected entry $path found in $f")
    }

  private def assertHasEntry(f: File, path: String) =
    withZipFile(f) { zf =>
      val ent = zf.getEntry(path)
      Predef.assert(ent != null, s"Entry $path not found in $f")
    }

  private def stringEntry(f: File, path: String) =
    withZipFile(f) { zf =>
      val ent = zf.getEntry(path)
      Predef.assert(ent != null, s"Entry $path not found in $f")

      new String(FileUtil.readFully(zf.getInputStream(ent)), StandardCharsets.UTF_8)
    }

  private def findInSource(f: File, needle: String, enc: String = "ISO_8859_1") =
    Using(scala.io.Source.fromFile(f, enc)) { s =>
      s.getLines().exists(line => line.contains(needle))
    }.get

  private def commandOutput(command: String*): String =
    commandOutput(new File("."), mergeError = false, expectedReturnCode = 0, command: _*)
  private def commandOutput(
    dir: File,
    mergeError: Boolean,
    expectedReturnCode: Int,
    command: String*
  ): String = {

    val b = new ProcessBuilder(command: _*)
    b.redirectInput(Redirect.INHERIT)
    b.redirectOutput(Redirect.PIPE)
    if (mergeError)
      b.redirectErrorStream(true)
    else
      b.redirectError(Redirect.INHERIT)
    b.directory(dir)
    val p    = b.start()
    val is   = p.getInputStream
    val baos = new ByteArrayOutputStream
    val buf  = Array.ofDim[Byte](16384)
    var read = -1
    while ({ read = is.read(buf); read >= 0 })
      baos.write(buf, 0, read)
    is.close()
    val retCode = p.waitFor()
    if (retCode == expectedReturnCode)
      new String(baos.toByteArray, StandardCharsets.UTF_8)
    else {
      val output = new String(baos.toByteArray, StandardCharsets.UTF_8)
      pprint.err.log(output)
      throw new Exception(
        s"Error while running ${command.mkString(" ")} (return code: $retCode, expected: $expectedReturnCode)"
      )
    }
  }

  private def assertNativeExecutable(file: File) = {

    // https://stackoverflow.com/questions/14799966/detect-an-executable-file-in-java/14800092#14800092

    val fis    = Files.newInputStream(file.toPath)
    val osName = sys.props("os.name").toLowerCase(Locale.ROOT)
    if (osName.contains("mac")) {
      val buf  = Array.fill[Byte](4)(0)
      val read = fis.read(buf)
      assert(read == 4)
      // cf fa ed fe
      val expected = Seq(0xcf, 0xfa, 0xed, 0xfe).map(_.toByte)
      assert(buf.toSeq == expected)
    }
    else if (osName.contains("linux")) {
      val buf  = Array.fill[Byte](4)(0)
      val read = fis.read(buf)
      assert(read == 4)
      // 7f 45 4c 46
      val expected = Seq(0x7f, 0x45, 0x4c, 0x46).map(_.toByte)
      assert(buf.toSeq == expected)
    }
    else if (osName.contains("windows")) {
      val buf  = Array.fill[Byte](2)(0)
      val read = fis.read(buf)
      assert(read == 2)
      // 4d 5a
      val expected = Seq(0x4d, 0x5a).map(_.toByte)
      assert(buf.toSeq == expected)
    }
    else
      sys.error(s"Unsupported OS: $osName")
    fis.close()
  }

  private def appInfo(raw: RawAppDescriptor, id: String): AppInfo = {
    val appDesc   = raw.appDescriptor.toOption.get
    val descRepr  = raw.repr.getBytes(StandardCharsets.UTF_8)
    val rawSource = RawSource(Nil, "inline", id)
    AppInfo(
      appDesc,
      descRepr,
      rawSource.source.toOption.getOrElse(???),
      rawSource.repr.getBytes(StandardCharsets.UTF_8)
    )
  }

  private def installDir(tmpDir: Path): InstallDir =
    installDir(tmpDir, "linux", "x86_64")
  private def installDir(tmpDir: Path, os: String, arch: String): InstallDir =
    InstallDir(tmpDir, cache)
      .withPlatform(Platform.get(os, arch))
      .withPlatformExtensions(InstallDir.platformExtensions(os))
      .withBasePreamble(Preamble())
      .withOverrideProguardedBootstraps {
        if (sys.props.get("java.version").exists(_.startsWith("1."))) None
        else Some(false)
      }

  private val currentArch =
    Option(System.getProperty("os.arch")).getOrElse("x86_64")

  private val currentOs = {
    val os = sys.props.getOrElse("os.name", "").toLowerCase(Locale.ROOT)
    if (os.contains("linux")) "linux"
    else if (os.contains("mac")) "mac"
    else if (os.contains("windows")) "windows"
    else sys.error(s"Unknown OS: '$os'")
  }

  val tests = Tests {
    test("generate an echo launcher") {
      def run(os: String, arch: String) = withTempDir { tmpDir =>

        val id = "echo"
        val appInfo0 = appInfo(
          RawAppDescriptor(List("io.get-coursier:echo:1.0.2"))
            .withRepositories(List("central")),
          id
        )

        val installDir0 = installDir(tmpDir, os, arch)

        val created = installDir0.createOrUpdate(appInfo0)
        assert(created.exists(identity))

        val launcher = installDir0.actualDest(id)
        assert(Files.isRegularFile(launcher))

        val urls = stringEntry(launcher.toFile, "coursier/bootstrap/launcher/bootstrap-jar-urls")
          .split('\n')
          .filter(_.nonEmpty)
          .toSeq
        val expectedUrls =
          Seq("https://repo1.maven.org/maven2/io/get-coursier/echo/1.0.2/echo-1.0.2.jar")
        assert(urls == expectedUrls)

        if (currentOs == os) {
          val output         = commandOutput(launcher.toAbsolutePath.toString, "-n", "foo")
          val expectedOutput = "foo"
          assert(output == expectedOutput)
        }

        val appList         = installDir0.list()
        val expectedAppList = Seq(id)
        assert(appList == expectedAppList)
      }

      test("linux") {
        run("linux", "x86_84")
      }
      test("mac") {
        run("mac", "x86_84")
      }
      test("windows") {
        run("windows", "x86_84")
      }
    }

    test("generate an echo assembly") {
      def run(os: String, arch: String) = withTempDir { tmpDir =>

        val id = "echo"
        val appInfo0 = appInfo(
          RawAppDescriptor(List("io.get-coursier:echo:1.0.2"))
            .withRepositories(List("central"))
            .withLauncherType("assembly"),
          id
        )

        val installDir0 = installDir(tmpDir, os, arch)

        val created = installDir0.createOrUpdate(appInfo0)
        assert(created.exists(identity))

        val launcher = installDir0.actualDest(id)
        assert(Files.isRegularFile(launcher))

        assertHasEntry(launcher.toFile, "coursier/echo/Echo.class")

        if (currentOs == os) {
          val output         = commandOutput(launcher.toAbsolutePath.toString, "-n", "foo")
          val expectedOutput = "foo"
          assert(output == expectedOutput)
        }
      }

      test("linux") {
        run("linux", "x86_84")
      }
      test("mac") {
        run("mac", "x86_84")
      }
      test("windows") {
        run("windows", "x86_84")
      }
    }

    test("generate an echo standalone launcher") {
      def run(os: String, arch: String) = withTempDir { tmpDir =>

        val id = "echo"
        val appInfo0 = appInfo(
          RawAppDescriptor(List("io.get-coursier:echo:1.0.2"))
            .withRepositories(List("central"))
            .withLauncherType("standalone"),
          id
        )

        val installDir0 = installDir(tmpDir, os, arch)

        val created = installDir0.createOrUpdate(appInfo0)
        assert(created.exists(identity))

        val launcher = installDir0.actualDest(id)
        assert(Files.isRegularFile(launcher))

        assertHasEntry(launcher.toFile, "coursier/bootstrap/launcher/ResourcesLauncher.class")
        assertHasEntry(launcher.toFile, "coursier/bootstrap/launcher/jars/echo-1.0.2.jar")
        val bootResources =
          stringEntry(launcher.toFile, "coursier/bootstrap/launcher/bootstrap-jar-resources")
            .split('\n')
            .filter(_.nonEmpty)
            .toSeq
        val expectedBootResources = Seq("echo-1.0.2.jar")
        assert(bootResources == expectedBootResources)

        if (currentOs == os) {
          val output         = commandOutput(launcher.toAbsolutePath.toString, "-n", "foo")
          val expectedOutput = "foo"
          assert(output == expectedOutput)
        }
      }

      test("linux") {
        run("linux", "x86_84")
      }
      test("mac") {
        run("mac", "x86_84")
      }
      test("windows") {
        run("windows", "x86_84")
      }
    }

    test("not update an already up-to-date launcher") {
      def run(os: String, arch: String) = withTempDir { tmpDir =>

        val id = "echo"
        val appInfo0 = appInfo(
          RawAppDescriptor(List("io.get-coursier:echo:1.0.2"))
            .withRepositories(List("central")),
          id
        )

        val installDir0 = installDir(tmpDir, os, arch)
          .withVerbosity(1)

        val created = installDir0.createOrUpdate(appInfo0)
        assert(created.exists(identity))

        val launcher = installDir0.actualDest(id)
        assert(Files.isRegularFile(launcher))

        def testRun(): Unit = {
          val output         = commandOutput(launcher.toAbsolutePath.toString, "-n", "foo")
          val expectedOutput = "foo"
          assert(output == expectedOutput)
        }

        if (currentOs == os)
          testRun()

        val updated = installDir0.createOrUpdate(appInfo0)
        assert(updated.exists(!_))

        if (currentOs == os)
          testRun()
      }

      test("linux") {
        run("linux", "x86_64")
      }
      test("mac") {
        run("mac", "x86_64")
      }
      test("windows") {
        run("windows", "x86_64")
      }
    }

    test("update a launcher") {
      def run(os: String, arch: String) = withTempDir { tmpDir =>

        val id = "echo"
        val appInfo0 = appInfo(
          RawAppDescriptor(List("io.get-coursier:echo:1.0.1"))
            .withRepositories(List("central"))
            .withLauncherType("standalone"), // easier to test
          id
        )

        val installDir0 = installDir(tmpDir, os, arch)
          .withVerbosity(1)

        val now = {
          val t = Instant.now()
          t.minusNanos(t.getNano) // seems nano part isn't persisted
        }

        val created = installDir0.createOrUpdate(appInfo0, currentTime = now.plusSeconds(-30))
        assert(created.exists(identity))

        val launcher = installDir0.actualDest(id)
        Predef.assert(
          Files.getLastModifiedTime(launcher).toInstant == now.plusSeconds(-30),
          s"now=$now, 30s before=${now.plusSeconds(-30)}"
        )

        assertHasEntry(launcher.toFile, "coursier/bootstrap/launcher/jars/echo-1.0.1.jar")
        assertHasNotEntry(launcher.toFile, "coursier/bootstrap/launcher/jars/echo-1.0.2.jar")

        def testRun(): Unit = {
          val output         = commandOutput(launcher.toAbsolutePath.toString, "-n", "foo")
          val expectedOutput = "foo"
          assert(output == expectedOutput)
        }

        if (currentOs == os)
          testRun()

        val newAppInfo = appInfo(
          RawAppDescriptor(List("io.get-coursier:echo:1.0.2")) // bump version
            .withRepositories(List("central"))
            .withLauncherType("standalone"), // easier to test
          "echo"
        )

        val updated = mayThrow {
          installDir0.maybeUpdate(
            id,
            src =>
              if (src == newAppInfo.source)
                Task.point(Some(("inline", newAppInfo.appDescriptorBytes)))
              else
                Task.fail(new Exception(s"Invalid source: $src")),
            currentTime = now
          ).unsafeRun(wrapExceptions = true)(cache.ec)
        }

        // randomly seeing the old file on OS X if we don't check that :|
        assert(Files.getLastModifiedTime(launcher).toInstant == now)

        assert(updated.exists(identity))
        assertHasNotEntry(launcher.toFile, "coursier/bootstrap/launcher/jars/echo-1.0.1.jar")
        assertHasEntry(launcher.toFile, "coursier/bootstrap/launcher/jars/echo-1.0.2.jar")

        if (currentOs == os)
          testRun()
      }

      test("linux") {
        run("linux", "x86_64")
      }
      test("mac") {
        run("mac", "x86_64")
      }
      test("windows") {
        run("windows", "x86_64")
      }
    }

    test("try updating a non-installed app") {
      def run(os: String, arch: String) = withTempDir { tmpDir =>
        val installDir0 = installDir(tmpDir, os, arch)
          .withVerbosity(1)

        val result =
          installDir0.maybeUpdate(
            "dummy-app-id",
            src => sys.error("illegal: that code should not be reached")
          ).attempt.unsafeRun(wrapExceptions = true)(cache.ec)

        assert(
          result.contains(Some(false))
        ) // TODO Check the output contains "Cannot find installed application 'dummy-app-id'..."
      }

      test("linux") {
        run("linux", "x86_64")
      }
      test("mac") {
        run("mac", "x86_64")
      }
      test("windows") {
        run("windows", "x86_64")
      }
    }

    test("install a prebuilt launcher") {
      def run(os: String, arch: String) = withTempDir { tmpDir =>

        val id    = "coursier"
        val csUrl = "https://github.com/coursier/coursier/releases/download/v2.0.0/coursier"
        val appInfo0 = appInfo(
          RawAppDescriptor(List("io.get-coursier:echo:1.0.1"))
            .withRepositories(List("central"))
            .withLauncherType("graalvm-native-image")
            .withPrebuiltBinaries(Map(
              "x86_64-apple-darwin" -> csUrl,
              "x86_64-pc-linux"     -> csUrl,
              "x86_64-pc-win32"     -> csUrl
            )),
          id
        )

        val installDir0 = installDir(tmpDir, os, arch)
          .withVerbosity(1)
          .withOnlyPrebuilt(true)

        val created = installDir0.createOrUpdate(appInfo0)
        assert(created.exists(identity))

        val launcher = installDir0.actualDest(id)

        def testRun(): Unit = {
          val output              = commandOutput(launcher.toAbsolutePath.toString, "--help")
          val expectedStartOutput = "Coursier 2.0.0"
          assert(output.startsWith(expectedStartOutput))
        }

        // No lightweight native Windows executable in the test fixtures
        if (currentOs == os && currentOs != "windows")
          testRun()
      }

      test("linux") {
        run("linux", "x86_64")
      }
      test("mac") {
        run("mac", "x86_64")
      }
      test("windows") {
        run("windows", "x86_64")
      }
    }

    test("install a compressed prebuilt launcher") {
      def run(os: String, arch: String) = withTempDir { tmpDir =>

        val id = "sbtn"
        val appInfo0 = appInfo(
          RawAppDescriptor(List("org.scala-sbt:sbt:1.4.0"))
            .withRepositories(List("central"))
            .withLauncherType("graalvm-native-image")
            .withPrebuiltBinaries(Map(
              "x86_64-apple-darwin" -> "tgz+https://github.com/sbt/sbtn-dist/releases/download/v${version}/sbtn-${platform}-${version}.tar.gz",
              "x86_64-pc-linux" -> "tgz+https://github.com/sbt/sbtn-dist/releases/download/v${version}/sbtn-${platform}-${version}.tar.gz",
              "x86_64-pc-win32" -> "zip+https://github.com/sbt/sbtn-dist/releases/download/v${version}/sbtn-${platform}-${version}.zip"
            )),
          id
        )

        val installDir0 = installDir(tmpDir, os, arch)
          .withVerbosity(1)
          .withOnlyPrebuilt(true)

        val created = installDir0.createOrUpdate(appInfo0)
        assert(created.exists(identity))

        val launcher = installDir0.actualDest(id)

        def testRun(): Unit = {
          val expectedRetCode = if (Properties.isWin) 0 else 1
          val output = commandOutput(
            tmpDir.toFile,
            mergeError = true,
            expectedReturnCode = expectedRetCode,
            launcher.toAbsolutePath.toString,
            "--help"
          )
          val expectedInOutput =
            if (Properties.isWin) "Failed to get console mode:"
            else "entering *experimental* thin client - BEEP WHIRR"
          assert(output.contains(expectedInOutput))
        }

        if (currentOs == os && currentOs != "windows" && currentArch == arch)
          testRun()
      }

      test("linux") {
        run("linux", "x86_64")
      }
      test("mac") {
        run("mac", "x86_64")
      }
      test("windows") {
        run("windows", "x86_64")
      }
    }

    test("install a prebuilt launcher in an archive") {
      val zipPattern =
        "zip+https://github.com/sbt/sbt/releases/download/v${version}/sbt-${version}.zip!sbt/bin/sbtn-${platform}"
      val tgzPattern =
        "tgz+https://github.com/sbt/sbt/releases/download/v${version}/sbt-${version}.tgz!sbt/bin/sbtn-${platform}"

      def run(os: String, arch: String, pattern: String) = withTempDir { tmpDir =>

        val id = "sbtn"
        val appInfo0 = appInfo(
          RawAppDescriptor(List("org.scala-sbt:sbt:1.4.1"))
            .withRepositories(List("central"))
            .withLauncherType("graalvm-native-image")
            .withPrebuilt(Some(pattern)),
          id
        )

        val installDir0 = installDir(tmpDir, os, arch)
          .withVerbosity(1)
          .withOnlyPrebuilt(true)

        val created = installDir0.createOrUpdate(appInfo0)
        assert(created.exists(identity))

        val launcher = installDir0.actualDest(id)

        def testRun(): Unit = {
          val expectedRetCode = if (Properties.isWin) 0 else 1
          val output = commandOutput(
            tmpDir.toFile,
            mergeError = true,
            expectedReturnCode = expectedRetCode,
            launcher.toAbsolutePath.toString,
            "--help"
          )
          val expectedInOutput =
            if (Properties.isWin) "Failed to get console mode:"
            else "entering *experimental* thin client - BEEP WHIRR"
          assert(output.contains(expectedInOutput))
        }

        if (currentOs == os && currentOs != "windows" && currentArch == arch)
          testRun()
      }

      test("zip") {
        test("linux") {
          run("linux", "x86_64", zipPattern)
        }
        test("mac") {
          run("mac", "x86_64", zipPattern)
        }
        test("windows") {
          run("windows", "x86_64", zipPattern)
        }
      }

      test("tgz") {
        test("linux") {
          run("linux", "x86_64", tgzPattern)
        }
        test("mac") {
          run("mac", "x86_64", tgzPattern)
        }
        test("windows") {
          run("windows", "x86_64", tgzPattern)
        }
      }
    }

    test("install a prebuilt gzip-ed / zip-ed launcher") {
      def run(os: String, arch: String) = withTempDir { tmpDir =>

        val id = "scalafmt-native"
        val appInfo0 = appInfo(
          RawAppDescriptor(List("org.scalameta::scalafmt-cli:3.0.6"))
            .withRepositories(List("central"))
            .withLauncherType("graalvm-native-image")
            .withPrebuiltBinaries(
              Map(
                "x86_64-apple-darwin" -> "gz+https://github.com/scala-cli/scalafmt-native-image/releases/download/v3.0.6/scalafmt-x86_64-apple-darwin.gz",
                "x86_64-pc-linux" -> "gz+https://github.com/scala-cli/scalafmt-native-image/releases/download/v3.0.6/scalafmt-x86_64-pc-linux.gz",
                "x86_64-pc-win32" -> "zip+https://github.com/scala-cli/scalafmt-native-image/releases/download/v3.0.6/scalafmt-x86_64-pc-win32.zip"
              )
            ),
          id
        )

        val installDir0 = installDir(tmpDir, os, arch)
          .withVerbosity(1)
          .withOnlyPrebuilt(true)

        val created = installDir0.createOrUpdate(appInfo0)
        assert(created.exists(identity))

        val launcher = installDir0.actualDest(id)

        def testRun(): Unit = {
          val output = commandOutput(
            tmpDir.toFile,
            mergeError = true,
            expectedReturnCode = 0,
            launcher.toAbsolutePath.toString,
            "--help"
          )
          val expectedInOutput = "scalafmt 3.0.6"
          assert(output.contains(expectedInOutput))
        }

        if (currentOs == os && currentArch == arch)
          testRun()
      }

      test("linux") {
        run("linux", "x86_64")
      }
      test("mac") {
        run("mac", "x86_64")
      }
      test("windows") {
        run("windows", "x86_64")
      }
    }

    test("install a prebuilt-only zip-ed launcher") {
      def run(os: String, arch: String) = withTempDir { tmpDir =>

        val id = "sbt"
        val appInfo0 = appInfo(
          RawAppDescriptor(List("org.scala-sbt:sbt:1.4.1"))
            .withRepositories(List("central"))
            .withLauncherType("prebuilt")
            .withPrebuilt(Some(
              "zip+https://github.com/sbt/sbt/releases/download/v${version}/sbt-${version}.zip!sbt/bin/sbt"
            )),
          id
        )

        val installDir0 = installDir(tmpDir, os, arch)
          .withVerbosity(1)

        val created = installDir0.createOrUpdate(appInfo0)
        assert(created.exists(identity))

        val launcher = installDir0.actualDest(id)

        def testRun(): Unit = {
          val output = commandOutput(
            tmpDir.toFile,
            mergeError = true,
            expectedReturnCode = 0,
            launcher.toAbsolutePath.toString,
            "-version"
          )
          val expectedInOutput = "sbt script version: 1.4.1"
          assert(output.contains(expectedInOutput))
        }

        if (currentOs == os)
          testRun()
      }

      test("linux") {
        run("linux", "x86_64")
      }
      test("mac") {
        run("mac", "x86_64")
      }
      test("windows") {
        run("windows", "x86_64")
      }
    }

    // test("generate a native echo launcher via native-image") {
    //   withTempDir { tmpDir =>
    //     val id = "echo"
    //     val appInfo0 = appInfo(
    //       RawAppDescriptor(List("io.get-coursier:echo:1.0.2"))
    //         .withRepositories(List("central"))
    //         .withLauncherType("graalvm-native-image"),
    //       id
    //     )

    //     val installDir0 = installDir(tmpDir)
    //       .withVerbosity(1)
    //       .withGraalvmParamsOpt {
    //         Option(System.getenv("GRAALVM_HOME"))
    //           .orElse {
    //             val isGraalVM = Option(System.getProperty("java.vm.name"))
    //               .map(_.toLowerCase(Locale.ROOT))
    //               .exists(_.contains("graal"))
    //             if (isGraalVM)
    //               Option(System.getenv("JAVA_HOME"))
    //                 .orElse(Option(System.getProperty("java.home")))
    //             else
    //               None
    //           }
    //           .map(GraalvmParams(_, Nil))
    //       }
    //     )

    //     val created = installDir0.createOrUpdate(appInfo0)
    //     assert(created)

    //     val launcher = installDir0.actualDest(id)
    //     assert(Files.isRegularFile(launcher))

    //     assertNativeExecutable(launcher.toFile)

    //     val output = commandOutput(launcher.toAbsolutePath.toString, "-n", "foo")
    //     val expectedOutput = "foo"
    //     assert(output == expectedOutput)
    //   }
    // }

    test("refuse to delete a file not created by us") {
      def run(os: String, arch: String) = withTempDir { tmpDir =>

        val installDir0 = installDir(tmpDir, os, arch)
          .withVerbosity(1)

        val app = installDir0.actualDest("foo")
        Files.write(app, Array.emptyByteArray)

        val gotException =
          try {
            installDir0.delete("foo")
            false
          }
          catch {
            case _: NotAnApplication =>
              true
          }

        assert(gotException)
      }

      test("linux") {
        run("linux", "x86_64")
      }
      test("mac") {
        run("mac", "x86_64")
      }
      test("windows") {
        run("windows", "x86_64")
      }
    }

    test("install and override and update scalac") {
      def run(os: String, arch: String) = withTempDir { tmpDir =>
        val id = "scalac"
        val versionOverride =
          RawAppDescriptor.RawVersionOverride("(,2.max]")
            .withLauncherType(Some("bootstrap"))
            .withDependencies(Some(List("org.scala-lang:scala-compiler:2.12.8")))
            .withMainClass(Some("scala.tools.nsc.Main"))
            .withProperties(Some(RawAppDescriptor.Properties(
              Seq("scala.usejavacp" -> "true")
            )))
        val appInfo0 = appInfo(
          RawAppDescriptor(List("org.scala-lang:scala3-compiler_3:3.3.3"))
            .withRepositories(List("central"))
            .withLauncherType("prebuilt")
            .withPrebuilt(Some(
              "zip+https://github.com/scala/scala3/releases/download/${version}/scala3-${version}.zip!scala3-${version}/bin/scalac"
            ))
            .withVersionOverrides(List(versionOverride)),
          id
        )

        val installDir0 = installDir(tmpDir, os, arch)
          .withVerbosity(1)

        val created = installDir0.createOrUpdate(appInfo0)
        assert(created.exists(identity))

        val launcher = installDir0.actualDest(id)
        assert(Files.isRegularFile(launcher))

        // for a prebuilt launcher, we expect the path of the launcher to appear somewhere in the script
        val scala3path = {
          val original =
            "github.com/scala/scala3/releases/download/3.3.3/scala3-3.3.3.zip/scala3-3.3.3/bin/scala"
          if (currentOs == "windows") original.replace('/', '\\')
          else original
        }

        def testRun(expectedUrls: Seq[String], expectedProperties: Seq[String]): Unit = {
          assert(Files.isRegularFile(launcher))

          val urls = stringEntry(launcher.toFile, "coursier/bootstrap/launcher/bootstrap-jar-urls")
            .split('\n')
            .filter(_.nonEmpty)
            .toSeq
          assert(urls == expectedUrls)

          val properties =
            stringEntry(launcher.toFile, "coursier/bootstrap/launcher/bootstrap.properties")
              .split('\n')
              .filter(_.nonEmpty)
              .toSeq
          assert(properties == expectedProperties)
        }

        def searchInScript(needle: String): Unit = {
          assert(Files.isRegularFile(launcher))

          val foundNeedle = findInSource(launcher.toFile(), needle)
          val expected    = true
          assert(foundNeedle == expected)
        }

        def testOutput(expectedInOut: String): Unit = {
          val output = commandOutput(
            tmpDir.toFile,
            mergeError = true,
            expectedReturnCode = 0,
            launcher.toAbsolutePath.toString,
            "-version"
          )
          if (!output.contains(expectedInOut)) {
            pprint.err.log(expectedInOut)
            pprint.err.log(output)
          }
          assert(output.contains(expectedInOut))
        }

        /* issues on the Windows CI */
        val maybeTestOutput = System.getenv("CI") == null || currentOs != "windows"

        searchInScript(scala3path)
        if (currentOs == os && maybeTestOutput)
          testOutput("Scala compiler version 3.3.3 -- Copyright 2002-2024, LAMP/EPFL")

        val overridenAppInfo = appInfo0.overrideVersion("2.12.8")
        val overridden       = installDir0.createOrUpdate(overridenAppInfo)
        assert(overridden.exists(identity))

        val scala2CompilerJars =
          Seq(
            "https://repo1.maven.org/maven2/org/scala-lang/scala-compiler/2.12.8/scala-compiler-2.12.8.jar",
            "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.12.8/scala-library-2.12.8.jar",
            "https://repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.12.8/scala-reflect-2.12.8.jar",
            "https://repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.12/1.0.6/scala-xml_2.12-1.0.6.jar"
          )
        val scala2Properties =
          Seq(
            "bootstrap.mainClass=scala.tools.nsc.Main",
            "scala.usejavacp=true",
            "scala-compiler.version=2.12.8"
          )

        testRun(scala2CompilerJars, scala2Properties)

        val updated = installDir0.createOrUpdate(appInfo0)
        assert(updated.exists(identity))

        searchInScript(scala3path)
        if (currentOs == os && maybeTestOutput)
          testOutput("Scala compiler version 3.3.3 -- Copyright 2002-2024, LAMP/EPFL")
      }

      test("linux") {
        run("linux", "x86_64")
      }
      test("mac") {
        run("mac", "x86_64")
      }
      test("windows") {
        run("windows", "x86_64")
      }
    }

    test("override prebuilt / prebuiltBinaries") {
      val id = "cs"
      val versionOverride =
        RawAppDescriptor.RawVersionOverride("(,2.0.16]")
          .withPrebuilt(Some(
            "https://github.com/coursier/coursier/releases/download/v${version}/cs-${platform}"
          ))
          .withPrebuiltBinaries(Some(Map()))
      val appInfo0 = appInfo(
        RawAppDescriptor(List("io.get-coursier::coursier-cli:latest.release"))
          .withRepositories(List("central", "typesafe:ivy-releases"))
          .withName(Some("cs"))
          .withLauncherType("prebuilt")
          .withPrebuilt(None)
          .withPrebuiltBinaries(Map(
            "x86_64-pc-linux" -> "gz+https://github.com/coursier/coursier/releases/download/v${version}/cs-x86_64-pc-linux.gz",
            "x86_64-apple-darwin" -> "gz+https://github.com/coursier/coursier/releases/download/v${version}/cs-x86_64-apple-darwin.gz",
            "x86_64-pc-win32" -> "zip+https://github.com/coursier/coursier/releases/download/v${version}/cs-x86_64-pc-win32.zip"
          ))
          .withVersionOverrides(List(versionOverride)),
        id
      )

      def run(os: String, arch: String) = withTempDir { tmpDir =>
        val installDir0 = installDir(tmpDir, os, arch)
          .withVerbosity(1)

        val created = installDir0.createOrUpdate(appInfo0)
        assert(created.exists(identity))

        val launcher = installDir0.actualDest(id)
        assert(Files.isRegularFile(launcher))

        val ext               = if (os == "windows") ".exe" else ""
        val auxiliaryLauncher = launcher.getParent.resolve(InstallDir.auxName(id, ext))
        assert(Files.isRegularFile(auxiliaryLauncher))

        def testRun(expectedContent: String): Unit = {
          assert(Files.isRegularFile(auxiliaryLauncher))
          val content = new String(Files.readAllBytes(auxiliaryLauncher), StandardCharsets.UTF_8)
          val contentMatches = content == expectedContent
          assert(contentMatches)
        }

        val pf = installDir0.platform.getOrElse(sys.error("No platform?"))

        testRun(
          if (os == "linux")
            "https://github.com/coursier/coursier/releases/download/v2.1.25-M3/cs-x86_64-pc-linux.gz!cs-x86_64-pc-linux"
          else if (os == "mac")
            "https://github.com/coursier/coursier/releases/download/v2.1.25-M3/cs-x86_64-apple-darwin.gz!cs-x86_64-apple-darwin"
          else if (os == "windows")
            "https://github.com/coursier/coursier/releases/download/v2.1.25-M3/cs-x86_64-pc-win32.zip!cs-x86_64-pc-win32.exe"
          else
            sys.error(s"Unknown test os: $os")
        )

        val overridenAppInfo = appInfo0.overrideVersion("2.0.13")
        val overridden       = installDir0.createOrUpdate(overridenAppInfo)
        assert(overridden.exists(identity))

        testRun(s"https://github.com/coursier/coursier/releases/download/v2.0.13/cs-$pf$ext")

        val updated = installDir0.createOrUpdate(appInfo0)
        assert(updated.exists(identity))

        testRun(
          if (os == "linux")
            "https://github.com/coursier/coursier/releases/download/v2.1.25-M3/cs-x86_64-pc-linux.gz!cs-x86_64-pc-linux"
          else if (os == "mac")
            "https://github.com/coursier/coursier/releases/download/v2.1.25-M3/cs-x86_64-apple-darwin.gz!cs-x86_64-apple-darwin"
          else if (os == "windows")
            "https://github.com/coursier/coursier/releases/download/v2.1.25-M3/cs-x86_64-pc-win32.zip!cs-x86_64-pc-win32.exe"
          else
            sys.error(s"Unknown test os: $os")
        )
      }

      test("linux") {
        run("linux", "x86_64")
      }
      test("mac") {
        run("mac", "x86_64")
      }
      test("windows") {
        run("windows", "x86_64")
      }
    }
  }

  private def mayThrow[T](f: => T): T =
    try f
    catch {
      case NonFatal(e) =>
        throw new Exception(e)
    }

  // TODO
  //   should update launcher if the app description changes (change default main class?)
  //   should use found main class if it is found, and ignore default main class in that case
  //   should generate a graalvm native image
  //   should update graalvm native image if a new version is available
  //   should pick prebuilt launcher if available
  //   should not pick prebuilt launcher if not available
  //   should prefer to pick prebuilt launcher with ".exe" on Windows if available

}
