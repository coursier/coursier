package coursier.cli

import java.io._
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.zip.ZipInputStream

import caseapp.core.RemainingArgs
import coursier.cli.bootstrap.{Bootstrap, BootstrapOptions, BootstrapSpecificOptions}
import coursier.cli.options.{ArtifactOptions, RepositoryOptions, SharedLaunchOptions, SharedLoaderOptions}
import coursier.cli.resolve.SharedResolveOptions
import coursier.launcher.BootstrapGenerator.resourceDir
import utest._

/**
  * Bootstrap test is not covered by Pants because it does not prebuild a bootstrap.jar
  */
object CliBootstrapIntegrationTest extends TestSuite with CliTestLib {

  private def zipEntryContent(zis: ZipInputStream, path: String): Array[Byte] = {
    val e = zis.getNextEntry
    if (e == null)
      throw new NoSuchElementException(s"Entry $path in zip file")
    else if (e.getName == path)
      coursier.cache.internal.FileUtil.readFullyUnsafe(zis)
    else
      zipEntryContent(zis, path)
  }

  private def zipEntryNames(zis: ZipInputStream): Iterator[String] =
    new Iterator[String] {
      var e = zis.getNextEntry
      def hasNext: Boolean = e != null
      def next(): String = {
        val e0 = e
        e = zis.getNextEntry
        e0.getName
      }
    }

  private def actualContent(file: File) = {

    var fis: InputStream = null

    val content = coursier.cache.internal.FileUtil.readFully(new FileInputStream(file))

    val header = Seq[Byte](0x50, 0x4b, 0x03, 0x04)
    val idx = content.indexOfSlice(header)
    if (idx < 0)
      throw new Exception(s"ZIP header not found in ${file.getPath}")
    else
      content.drop(idx)
  }

  val tests = Tests {
  test("not add POMs to the classpath") - withFile() {

    (bootstrapFile, _) =>
      val repositoryOpt = RepositoryOptions(repository = List("bintray:scalameta/maven"))
      val artifactOptions = ArtifactOptions()
      val resolveOptions = SharedResolveOptions(
        repositoryOptions = repositoryOpt
      )
      val sharedLoaderOptions = SharedLoaderOptions(
        sharedTarget = List("foo"),
        isolated = List("foo:org.scalameta:trees_2.12:1.7.0")
      )
      val bootstrapSpecificOptions = BootstrapSpecificOptions(
        output = Some(bootstrapFile.getPath),
        force = true
      )
      val sharedLaunchOptions = SharedLaunchOptions(
        resolveOptions = resolveOptions,
        artifactOptions = artifactOptions,
        sharedLoaderOptions = sharedLoaderOptions
      )
      val bootstrapOptions = BootstrapOptions(
        sharedLaunchOptions = sharedLaunchOptions,
        options = bootstrapSpecificOptions
      )

      Bootstrap.run(
        bootstrapOptions,
        RemainingArgs(Seq("com.geirsson:scalafmt-cli_2.12:1.4.0"), Seq())
      )

      def zis = new ZipInputStream(new ByteArrayInputStream(actualContent(bootstrapFile)))

      val fooLines = Predef.augmentString(new String(zipEntryContent(zis, resourceDir + "bootstrap-jar-urls-1"), UTF_8)).lines.toVector
      val lines = Predef.augmentString(new String(zipEntryContent(zis, resourceDir + "bootstrap-jar-urls"), UTF_8)).lines.toVector

      assert(fooLines.exists(_.endsWith("/scalaparse_2.12-0.4.2.jar")))
      assert(!lines.exists(_.endsWith("/scalaparse_2.12-0.4.2.jar")))

      assert(!fooLines.exists(_.endsWith("/scalameta_2.12-1.7.0.jar")))
      assert(lines.exists(_.endsWith("/scalameta_2.12-1.7.0.jar")))

      // checking that there are no sources just in case…
      assert(!fooLines.exists(_.endsWith("/scalaparse_2.12-0.4.2-sources.jar")))
      assert(!lines.exists(_.endsWith("/scalaparse_2.12-0.4.2-sources.jar")))
      assert(!fooLines.exists(_.endsWith("/scalameta_2.12-1.7.0-sources.jar")))
      assert(!lines.exists(_.endsWith("/scalameta_2.12-1.7.0-sources.jar")))

      val extensions = fooLines
        .map { l =>
          val idx = l.lastIndexOf('.')
          if (idx < 0)
            l
          else
            l.drop(idx + 1)
        }
        .toSet

      assert(extensions == Set("jar"))
  }

  test("accept simple modules via --shared") - withFile() {

    (bootstrapFile, _) =>
      val repositoryOpt = RepositoryOptions(repository = List("bintray:scalameta/maven"))
      val artifactOptions = ArtifactOptions()
      val resolveOptions = SharedResolveOptions(
        repositoryOptions = repositoryOpt
      )
      val sharedLoaderOptions = SharedLoaderOptions(
        shared = List("org.scalameta:trees_2.12")
      )
      val bootstrapSpecificOptions = BootstrapSpecificOptions(
        output = Some(bootstrapFile.getPath),
        force = true
      )
      val sharedLaunchOptions = SharedLaunchOptions(
        resolveOptions = resolveOptions,
        artifactOptions = artifactOptions,
        sharedLoaderOptions = sharedLoaderOptions
      )
      val bootstrapOptions = BootstrapOptions(
        sharedLaunchOptions = sharedLaunchOptions,
        options = bootstrapSpecificOptions
      )

      Bootstrap.run(
        bootstrapOptions,
        RemainingArgs(Seq("com.geirsson:scalafmt-cli_2.12:1.4.0"), Seq())
      )

      def zis = new ZipInputStream(new ByteArrayInputStream(actualContent(bootstrapFile)))

      val fooLines = Predef.augmentString(new String(zipEntryContent(zis, resourceDir + "bootstrap-jar-urls-1"), UTF_8)).lines.toVector
      val lines = Predef.augmentString(new String(zipEntryContent(zis, resourceDir + "bootstrap-jar-urls"), UTF_8)).lines.toVector

      assert(fooLines.exists(_.endsWith("/scalaparse_2.12-0.4.2.jar")))
      assert(!lines.exists(_.endsWith("/scalaparse_2.12-0.4.2.jar")))

      assert(!fooLines.exists(_.endsWith("/scalameta_2.12-1.7.0.jar")))
      assert(lines.exists(_.endsWith("/scalameta_2.12-1.7.0.jar")))

      // checking that there are no sources just in case…
      assert(!fooLines.exists(_.endsWith("/scalaparse_2.12-0.4.2-sources.jar")))
      assert(!lines.exists(_.endsWith("/scalaparse_2.12-0.4.2-sources.jar")))
      assert(!fooLines.exists(_.endsWith("/scalameta_2.12-1.7.0-sources.jar")))
      assert(!lines.exists(_.endsWith("/scalameta_2.12-1.7.0-sources.jar")))

      val extensions = fooLines
        .map { l =>
          val idx = l.lastIndexOf('.')
          if (idx < 0)
            l
          else
            l.drop(idx + 1)
        }
        .toSet

      assert(extensions == Set("jar"))
  }

  test("add standard and source JARs to the classpath") - withFile() {

    (bootstrapFile, _) =>
      val repositoryOpt = RepositoryOptions(repository = List("bintray:scalameta/maven"))
      val artifactOptions = ArtifactOptions(
        sources = true,
        default = Some(true)
      )
      val resolveOptions = SharedResolveOptions(
        repositoryOptions = repositoryOpt
      )
      val bootstrapSpecificOptions = BootstrapSpecificOptions(
        output = Some(bootstrapFile.getPath),
        force = true
      )
      val sharedLaunchOptions = SharedLaunchOptions(
        resolveOptions = resolveOptions,
        artifactOptions = artifactOptions
      )
      val bootstrapOptions = BootstrapOptions(
        sharedLaunchOptions = sharedLaunchOptions,
        options = bootstrapSpecificOptions
      )

      Bootstrap.run(
        bootstrapOptions,
        RemainingArgs(Seq("com.geirsson:scalafmt-cli_2.12:1.4.0"), Seq())
      )

      val zis = new ZipInputStream(new ByteArrayInputStream(actualContent(bootstrapFile)))

      val lines = Predef.augmentString(new String(zipEntryContent(zis, resourceDir + "bootstrap-jar-urls"), UTF_8))
        .lines
        .toVector

      assert(lines.exists(_.endsWith("/scalaparse_2.12-0.4.2.jar")))
      assert(lines.exists(_.endsWith("/scalaparse_2.12-0.4.2-sources.jar")))
  }

  def isolationTest(standalone: Option[Boolean] = None): Unit =
    withFile() {

      (bootstrapFile, _) =>
        val repositoryOpt = RepositoryOptions(repository = List("bintray:scalameta/maven"))
        val artifactOptions = ArtifactOptions(
          sources = true,
          default = Some(true)
        )
        val resolveOptions = SharedResolveOptions(
          repositoryOptions = repositoryOpt
        )
        val sharedLoaderOptions = SharedLoaderOptions(
          sharedTarget = List("foo"),
          isolated = List("foo:org.scalameta:trees_2.12:1.7.0")
        )
        val bootstrapSpecificOptions = BootstrapSpecificOptions(
          output = Some(bootstrapFile.getPath),
          force = true,
          standalone = standalone
        )
        val sharedLaunchOptions = SharedLaunchOptions(
          resolveOptions = resolveOptions,
          artifactOptions = artifactOptions,
          sharedLoaderOptions = sharedLoaderOptions
        )
        val bootstrapOptions = BootstrapOptions(
          sharedLaunchOptions = sharedLaunchOptions,
          options = bootstrapSpecificOptions
        )

        Bootstrap.run(
          bootstrapOptions,
          RemainingArgs(Seq("com.geirsson:scalafmt-cli_2.12:1.4.0"), Seq())
        )

        def zis = new ZipInputStream(new ByteArrayInputStream(actualContent(bootstrapFile)))

        val suffix = if (standalone.exists(identity)) "resources" else "urls"
        val fooLines = Predef.augmentString(new String(zipEntryContent(zis, resourceDir + s"bootstrap-jar-$suffix-1"), UTF_8))
          .lines
          .toVector
          .map(_.replaceAll(".*/", ""))
        val lines = Predef.augmentString(new String(zipEntryContent(zis, resourceDir + s"bootstrap-jar-$suffix"), UTF_8))
          .lines
          .toVector
          .map(_.replaceAll(".*/", ""))

        assert(fooLines.contains("scalaparse_2.12-0.4.2.jar"))
        assert(fooLines.contains("scalaparse_2.12-0.4.2-sources.jar"))
        assert(!lines.contains("scalaparse_2.12-0.4.2.jar"))
        assert(!lines.contains("scalaparse_2.12-0.4.2-sources.jar"))

        assert(!fooLines.contains("scalameta_2.12-1.7.0.jar"))
        assert(!fooLines.contains("scalameta_2.12-1.7.0-sources.jar"))
        assert(lines.contains("scalameta_2.12-1.7.0.jar"))
        assert(lines.contains("scalameta_2.12-1.7.0-sources.jar"))
    }

  test("add standard and source JARs to the classpath with classloader isolation") {
    isolationTest()
  }

  test("add standard and source JARs to the classpath with classloader isolation in standalone bootstrap") {
    isolationTest(standalone = Some(true))
  }

  test("be deterministic when deterministic option is specified") - withFile() {(bootstrapFile, _) =>
    withFile() {(bootstrapFile2, _) =>
      val repositoryOpt = RepositoryOptions(repository = List("bintray:scalameta/maven"))
      val artifactOptions = ArtifactOptions(
        sources = true,
        default = Some(true)
      )
      val resolveOptions = SharedResolveOptions(
        repositoryOptions = repositoryOpt
      )
      val sharedLoaderOptions = SharedLoaderOptions(
        sharedTarget = List("foo"),
        isolated = List("foo:org.scalameta:trees_2.12:1.7.0")
      )
      val bootstrapSpecificOptions = BootstrapSpecificOptions(
        output = Some(bootstrapFile.getPath),
        force = true,
        deterministic = true
      )
      val sharedLaunchOptions = SharedLaunchOptions(
        resolveOptions = resolveOptions,
        artifactOptions = artifactOptions,
        sharedLoaderOptions = sharedLoaderOptions
      )
      val bootstrapOptions = BootstrapOptions(
        sharedLaunchOptions = sharedLaunchOptions,
        options = bootstrapSpecificOptions
      )
      Bootstrap.run(
        bootstrapOptions,
        RemainingArgs(Seq("com.geirsson:scalafmt-cli_2.12:1.4.0"), Seq())
      )

      //We need to wait between two runs to ensure we don't accidentally get the same hash
      Thread.sleep(2000)

      val bootstrapSpecificOptions2 = bootstrapSpecificOptions.copy(
        output = Some(bootstrapFile2.getPath)
      )
      val bootstrapOptions2 = bootstrapOptions.copy(
        options = bootstrapSpecificOptions2
      )
      Bootstrap.run(
        bootstrapOptions2,
        RemainingArgs(Seq("com.geirsson:scalafmt-cli_2.12:1.4.0"), Seq())
      )

      val bootstrap1SHA256 = MessageDigest.getInstance("SHA-256")
        .digest(actualContent(bootstrapFile))
        .toSeq
        .map(b => "%02x".format(b))
        .mkString

      val bootstrap2SHA256 = MessageDigest.getInstance("SHA-256")
        .digest(actualContent(bootstrapFile2))
        .toSeq
        .map(b => "%02x".format(b))
        .mkString

      assert(bootstrap1SHA256 == bootstrap2SHA256)
    }
  }

  test("rename JAR with the same file name") - withFile() {

    (bootstrapFile, _) =>
      val repositoryOpt = RepositoryOptions(repository = List("bintray:scalacenter/releases"))
      val resolveOptions = SharedResolveOptions(
        repositoryOptions = repositoryOpt
      )
      val bootstrapSpecificOptions = BootstrapSpecificOptions(
        output = Some(bootstrapFile.getPath),
        force = true,
        standalone = Some(true)
      )
      val sharedLaunchOptions = SharedLaunchOptions(
        resolveOptions = resolveOptions
      )
      val bootstrapOptions = BootstrapOptions(
        sharedLaunchOptions = sharedLaunchOptions,
        options = bootstrapSpecificOptions
      )

      Bootstrap.run(
        bootstrapOptions,
        RemainingArgs(Seq("org.scalameta:metals_2.12:0.2.0"), Seq())
      )

      val zis = new ZipInputStream(new ByteArrayInputStream(actualContent(bootstrapFile)))

      val lines = Predef.augmentString(new String(zipEntryContent(zis, resourceDir + "bootstrap-jar-resources"), UTF_8))
        .lines
        .toVector

      val fastparseLines = lines.filter(_.startsWith("fastparse_2.12-1.0.0"))
      val fastparseUtilsLines = lines.filter(_.startsWith("fastparse-utils_2.12-1.0.0"))

      assert(fastparseLines.length == 2)
      assert(fastparseLines.distinct.length == 2)
      assert(fastparseUtilsLines.length == 2)
      assert(fastparseUtilsLines.distinct.length == 2)
  }

  test("put everything under the coursier/bootstrap directory in bootstrap") - withFile() {
    (bootstrapFile, _) =>

      val sharedLoaderOptions = SharedLoaderOptions(
        sharedTarget = List("launcher"),
        isolated = List("launcher:org.scala-sbt:launcher-interface:1.0.4")
      )
      val sharedLaunchOptions = SharedLaunchOptions(
        sharedLoaderOptions = sharedLoaderOptions,
        property = List("jline.shutdownhook=false")
      )
      val bootstrapSpecificOptions = BootstrapSpecificOptions(
        output = Some(bootstrapFile.getPath),
        force = true,
        standalone = Some(true)
      )
      val bootstrapOptions = BootstrapOptions(
        sharedLaunchOptions = sharedLaunchOptions,
        options = bootstrapSpecificOptions
      )

      Bootstrap.run(
        bootstrapOptions,
        RemainingArgs(
          Seq(
            "io.get-coursier:sbt-launcher_2.12:1.1.0-M3",
            "io.get-coursier:coursier-okhttp_2.12:1.1.0-M9"
          ),
          Seq()
        )
      )

      val zis = new ZipInputStream(new ByteArrayInputStream(actualContent(bootstrapFile)))
      val names = zipEntryNames(zis).toVector
      assert(names.exists(_.startsWith("META-INF/")))
      assert(names.exists(_.startsWith("coursier/bootstrap/launcher/")))
      assert(names.forall(n => n.startsWith("META-INF/") || n.startsWith("coursier/bootstrap/launcher/")))
  }
  }
}
