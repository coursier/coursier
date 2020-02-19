package coursier.cli.install

import java.io.{ByteArrayOutputStream, File, FileInputStream}
import java.lang.ProcessBuilder.Redirect
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.time.Instant
import java.util.Locale
import java.util.zip.ZipFile

import coursier.cache.internal.FileUtil
import coursier.cache.{Cache, MockCache}
import coursier.install.{GraalvmParams, InstallDir, RawAppDescriptor}
import coursier.util.{Sync, Task}
import org.scalatest.{BeforeAndAfterAll, FlatSpec}

import scala.collection.JavaConverters._

class InstallTests extends FlatSpec with BeforeAndAfterAll {

  private val pool = Sync.fixedThreadPool(6)

  private val mockDataLocation = {
    val dir = Paths.get("modules/tests/metadata")
    assert(Files.isDirectory(dir))
    dir
  }

  private val writeMockData = sys.env
    .get("FETCH_MOCK_DATA")
    .exists(s => s == "1" || s.toLowerCase(Locale.ROOT) == "true")

  private val cache: Cache[Task] =
    MockCache.create[Task](mockDataLocation, writeMissing = writeMockData, pool = pool)

  private def delete(d: Path): Unit =
    if (Files.isDirectory(d)) {
      var s: java.util.stream.Stream[Path] = null
      try {
        s = Files.list(d)
        s.iterator()
          .asScala
          .foreach(delete)
      } finally {
        if (s != null)
          s.close()
      }
    } else
      Files.deleteIfExists(d)

  private def withTempDir[T](f: Path => T): T = {
    val tmpDir = Files.createTempDirectory("coursier-install-test")
    try f(tmpDir)
    finally {
      delete(tmpDir)
    }
  }

  private def assertHasNotEntry(f: File, path: String) = {
    val zf = new ZipFile(f)
    val ent = zf.getEntry(path)
    Predef.assert(ent == null, s"Unexpected entry $path found in $f")
  }

  private def assertHasEntry(f: File, path: String) = {
    val zf = new ZipFile(f)
    val ent = zf.getEntry(path)
    Predef.assert(ent != null, s"Entry $path not found in $f")
  }

  private def stringEntry(f: File, path: String) = {

    val zf = new ZipFile(f)
    val ent = zf.getEntry(path)
    Predef.assert(ent != null, s"Entry $path not found in $f")

    new String(FileUtil.readFully(zf.getInputStream(ent)), StandardCharsets.UTF_8)
  }

  private def commandOutput(command: String*): String = {

    val b = new ProcessBuilder(command: _*)
    b.redirectInput(Redirect.INHERIT)
    b.redirectOutput(Redirect.PIPE)
    b.redirectError(Redirect.INHERIT)
    val p = b.start()
    val is = p.getInputStream
    val baos = new ByteArrayOutputStream
    val buf = Array.ofDim[Byte](16384)
    var read = -1
    while ({ read = is.read(buf); read >= 0 })
      baos.write(buf, 0, read)
    is.close()
    val retCode = p.waitFor()
    if (retCode == 0)
      new String(baos.toByteArray, StandardCharsets.UTF_8)
    else
      throw new Exception(s"Error while running ${command.mkString(" ")} (return code: $retCode)")
  }

  private def assertNativeExecutable(file: File) = {

    // https://stackoverflow.com/questions/14799966/detect-an-executable-file-in-java/14800092#14800092

    val fis = new FileInputStream(file)
    val osName = sys.props("os.name").toLowerCase(Locale.ROOT)
    if (osName.contains("mac")) {
      val buf = Array.fill[Byte](4)(0)
      val read = fis.read(buf)
      assert(read == 4)
      // cf fa ed fe
      val expected = Seq(0xcf, 0xfa, 0xed, 0xfe).map(_.toByte)
      assert(buf.toSeq == expected)
    } else if (osName.contains("linux")) {
      val buf = Array.fill[Byte](4)(0)
      val read = fis.read(buf)
      assert(read == 4)
      // 7f 45 4c 46
      val expected = Seq(0x7f, 0x45, 0x4c, 0x46).map(_.toByte)
      assert(buf.toSeq == expected)
    } else if (osName.contains("windows")) {
      val buf = Array.fill[Byte](2)(0)
      val read = fis.read(buf)
      assert(read == 2)
      // 4d 5a
      val expected = Seq(0x4d, 0x5a).map(_.toByte)
      assert(buf.toSeq == expected)
    } else {
      sys.error(s"Unsupported OS: $osName")
    }
    fis.close()
  }

  private def appDescriptor(raw: RawAppDescriptor) = {
    val appDesc = raw.appDescriptor.toOption.get
    val descRepr = raw.repr.getBytes(StandardCharsets.UTF_8)
    (appDesc, descRepr)
  }

  override protected def afterAll(): Unit = {
    pool.shutdown()
  }

  it should "generate an echo launcher" in withTempDir { tmpDir =>

    val (appDesc, descRepr) = appDescriptor(
      RawAppDescriptor(List("io.get-coursier:echo:1.0.2"))
        .withRepositories(List("central"))
    )

    val launcher = tmpDir.resolve("echo")

    val installDir = InstallDir(tmpDir, cache)

    val created = installDir.createOrUpdate(
      Some((appDesc, descRepr)),
      None,
      launcher
    )

    assert(created.exists(identity))
    assert(Files.isRegularFile(launcher))

    val urls = stringEntry(launcher.toFile, "coursier/bootstrap/launcher/bootstrap-jar-urls")
      .split('\n')
      .filter(_.nonEmpty)
      .toSeq
    val expectedUrls = Seq("https://repo1.maven.org/maven2/io/get-coursier/echo/1.0.2/echo-1.0.2.jar")
    assert(urls == expectedUrls)

    val output = commandOutput(launcher.toAbsolutePath.toString, "-n", "foo")
    val expectedOutput = "foo"
    assert(output == expectedOutput)
  }

  it should "generate an echo assembly" in withTempDir { tmpDir =>

    val (appDesc, descRepr) = appDescriptor(
      RawAppDescriptor(List("io.get-coursier:echo:1.0.2"))
        .withRepositories(List("central"))
        .withLauncherType("assembly")
    )

    val launcher = tmpDir.resolve("echo")

    val installDir = InstallDir(tmpDir, cache)

    val created = installDir.createOrUpdate(
      Some((appDesc, descRepr)),
      None,
      launcher
    )

    assert(created.exists(identity))
    assert(Files.isRegularFile(launcher))

    assertHasEntry(launcher.toFile, "coursier/echo/Echo.class")

    val output = commandOutput(launcher.toAbsolutePath.toString, "-n", "foo")
    val expectedOutput = "foo"
    assert(output == expectedOutput)
  }

  it should "generate an echo standalone launcher" in withTempDir { tmpDir =>

    val (appDesc, descRepr) = appDescriptor(
      RawAppDescriptor(List("io.get-coursier:echo:1.0.2"))
        .withRepositories(List("central"))
        .withLauncherType("standalone")
    )

    val launcher = tmpDir.resolve("echo")

    val installDir = InstallDir(tmpDir, cache)

    val created = installDir.createOrUpdate(
      Some((appDesc, descRepr)),
      None,
      launcher
    )

    assert(created.exists(identity))
    assert(Files.isRegularFile(launcher))

    assertHasEntry(launcher.toFile, "coursier/bootstrap/launcher/ResourcesLauncher.class")
    assertHasEntry(launcher.toFile, "coursier/bootstrap/launcher/jars/echo-1.0.2.jar")
    val bootResources = stringEntry(launcher.toFile, "coursier/bootstrap/launcher/bootstrap-jar-resources")
      .split('\n')
      .filter(_.nonEmpty)
      .toSeq
    val expectedBootResources = Seq("echo-1.0.2.jar")
    assert(bootResources == expectedBootResources)

    val output = commandOutput(launcher.toAbsolutePath.toString, "-n", "foo")
    val expectedOutput = "foo"
    assert(output == expectedOutput)
  }

  it should "not update an already up-to-date launcher" in withTempDir { tmpDir =>

    val (appDesc, descRepr) = appDescriptor(
      RawAppDescriptor(List("io.get-coursier:echo:1.0.2"))
        .withRepositories(List("central"))
    )

    val launcher = tmpDir.resolve("echo")

    val installDir = InstallDir(tmpDir, cache)
      .withVerbosity(1)

    val created = installDir.createOrUpdate(
      Some((appDesc, descRepr)),
      None,
      launcher
    )

    assert(created.exists(identity))
    assert(Files.isRegularFile(launcher))

    def testRun(): Unit = {
      val output = commandOutput(launcher.toAbsolutePath.toString, "-n", "foo")
      val expectedOutput = "foo"
      assert(output == expectedOutput)
    }

    testRun()

    val updated = installDir.createOrUpdate(
      Some((appDesc, descRepr)),
      None,
      launcher
    )

    assert(updated.exists(!_))

    testRun()
  }

  it should "update a launcher" in withTempDir { tmpDir =>

    val (appDesc, descRepr) = appDescriptor(
      RawAppDescriptor(List("io.get-coursier:echo:1.0.1"))
        .withRepositories(List("central"))
        .withLauncherType("standalone") // easier to test
    )

    val launcher = tmpDir.resolve("echo")

    val now = {
      val t = Instant.now()
      t.minusNanos(t.getNano) // seems nano part isn't persisted
    }

    val installDir = InstallDir(tmpDir, cache)
      .withVerbosity(1)

    val created = installDir.createOrUpdate(
      Some((appDesc, descRepr)),
      None,
      launcher,
      currentTime = now.plusSeconds(-30)
    )

    Predef.assert(Files.getLastModifiedTime(launcher).toInstant == now.plusSeconds(-30), s"now=$now, 30s before=${now.plusSeconds(-30)}")
    assert(created.exists(identity))
    assertHasEntry(launcher.toFile, "coursier/bootstrap/launcher/jars/echo-1.0.1.jar")
    assertHasNotEntry(launcher.toFile, "coursier/bootstrap/launcher/jars/echo-1.0.2.jar")

    def testRun(): Unit = {
      val output = commandOutput(launcher.toAbsolutePath.toString, "-n", "foo")
      val expectedOutput = "foo"
      assert(output == expectedOutput)
    }

    testRun()

    val (newAppDesc, newDescRepr) = appDescriptor(
      RawAppDescriptor(List("io.get-coursier:echo:1.0.2")) // bump version
        .withRepositories(List("central"))
        .withLauncherType("standalone") // easier to test
    )

    val updated = installDir.createOrUpdate(
      Some((newAppDesc, newDescRepr)),
      None,
      launcher,
      currentTime = now
    )

    // randomly seeing the old file on OS X if we don't check that :|
    assert(Files.getLastModifiedTime(launcher).toInstant == now)

    assert(updated.exists(identity))
    assertHasNotEntry(launcher.toFile, "coursier/bootstrap/launcher/jars/echo-1.0.1.jar")
    assertHasEntry(launcher.toFile, "coursier/bootstrap/launcher/jars/echo-1.0.2.jar")

    testRun()
  }

  // it should "generate a native echo launcher via native-image" in withTempDir { tmpDir =>

  //   val (appDesc, descRepr) = appDescriptor(
  //     RawAppDescriptor(List("io.get-coursier:echo:1.0.2"))
  //       .withRepositories(List("central"))
  //       .withLauncherType("graalvm-native-image")
  //   )

  //   val launcher = tmpDir.resolve("echo")

  //   val installDir = InstallDir(tmpDir, cache)
  //     .withVerbosity(1)
  //     .withGraalvmParamsOpt {
  //       Option(System.getenv("GRAALVM_HOME"))
  //         .orElse {
  //           val isGraalVM = Option(System.getProperty("java.vm.name"))
  //             .map(_.toLowerCase(Locale.ROOT))
  //             .exists(_.contains("graal"))
  //           if (isGraalVM)
  //             Option(System.getenv("JAVA_HOME"))
  //               .orElse(Option(System.getProperty("java.home")))
  //           else
  //             None
  //         }
  //         .map(GraalvmParams(_, Nil))
  //     }
  //   )

  //   val created = installDir.createOrUpdate(
  //     Some((appDesc, descRepr)),
  //     None,
  //     launcher
  //   )

  //   assert(created)
  //   assert(Files.isRegularFile(launcher))

  //   assertNativeExecutable(launcher.toFile)

  //   val output = commandOutput(launcher.toAbsolutePath.toString, "-n", "foo")
  //   val expectedOutput = "foo"
  //   assert(output == expectedOutput)
  // }

  it should "refuse to delete a file not created by us" in withTempDir { tmpDir =>

    val app = tmpDir.resolve("foo")
    Files.write(app, Array.emptyByteArray)

    val installDir = InstallDir(tmpDir, cache)
      .withVerbosity(1)

    val gotException = try {
      installDir.delete("foo")
      false
    } catch {
      case _: InstallDir.NotAnApplication =>
        true
    }

    assert(gotException)
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
