package coursier.cli

import java.io._
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import java.util.zip.ZipInputStream

import caseapp.core.RemainingArgs
import coursier.cli.options._
import coursier.cli.options.shared.RepositoryOptions
import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import scala.collection.JavaConverters._

/**
  * Bootstrap test is not covered by Pants because it does not prebuild a bootstrap.jar
  */
@RunWith(classOf[JUnitRunner])
class CliBootstrapIntegrationTest extends FlatSpec with CliTestLib {

  private def zipEntryContent(zis: ZipInputStream, path: String): Array[Byte] = {
    val e = zis.getNextEntry
    if (e == null)
      throw new NoSuchElementException(s"Entry $path in zip file")
    else if (e.getName == path)
      coursier.internal.FileUtil.readFully(zis)
    else
      zipEntryContent(zis, path)
  }

  private def actualContent(file: File) = {

    var fis: InputStream = null

    val content = try {
      fis = new FileInputStream(file)
      coursier.internal.FileUtil.readFully(fis)
    } finally {
      if (fis != null) fis.close()
    }

    val header = Seq[Byte](0x50, 0x4b, 0x03, 0x04)
    val idx = content.indexOfSlice(header)
    if (idx < 0)
      throw new Exception(s"ZIP header not found in ${file.getPath}")
    else
      content.drop(idx)
  }

  "bootstrap" should "not add POMs to the classpath" in withFile() {

    (bootstrapFile, _) =>
      val repositoryOpt = RepositoryOptions(repository = List("bintray:scalameta/maven"))
      val artifactOptions = ArtifactOptions()
      val common = CommonOptions(
        repositoryOptions = repositoryOpt
      )
      val isolatedLoaderOptions = IsolatedLoaderOptions(
        isolateTarget = List("foo"),
        isolated = List("foo:org.scalameta:trees_2.12:1.7.0")
      )
      val bootstrapSpecificOptions = BootstrapSpecificOptions(
        output = bootstrapFile.getPath,
        isolated = isolatedLoaderOptions,
        force = true,
        common = common
      )
      val bootstrapOptions = BootstrapOptions(artifactOptions, bootstrapSpecificOptions)

      Bootstrap.bootstrap(
        bootstrapOptions,
        RemainingArgs(Seq("com.geirsson:scalafmt-cli_2.12:1.4.0"), Seq())
      )

      def zis = new ZipInputStream(new ByteArrayInputStream(actualContent(bootstrapFile)))

      val fooLines = Predef.augmentString(new String(zipEntryContent(zis, "bootstrap-isolation-foo-jar-urls"), UTF_8)).lines.toVector
      val lines = Predef.augmentString(new String(zipEntryContent(zis, "bootstrap-jar-urls"), UTF_8)).lines.toVector

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

  "bootstrap" should "add standard and source JARs to the classpath" in withFile() {

    (bootstrapFile, _) =>
      val repositoryOpt = RepositoryOptions(repository = List("bintray:scalameta/maven"))
      val artifactOptions = ArtifactOptions(
        sources = true,
        default = Some(true)
      )
      val common = CommonOptions(
        repositoryOptions = repositoryOpt
      )
      val bootstrapSpecificOptions = BootstrapSpecificOptions(
        output = bootstrapFile.getPath,
        force = true,
        common = common
      )
      val bootstrapOptions = BootstrapOptions(artifactOptions, bootstrapSpecificOptions)

      Bootstrap.bootstrap(
        bootstrapOptions,
        RemainingArgs(Seq("com.geirsson:scalafmt-cli_2.12:1.4.0"), Seq())
      )

      val zis = new ZipInputStream(new ByteArrayInputStream(actualContent(bootstrapFile)))

      val lines = new String(zipEntryContent(zis, "bootstrap-jar-urls"), UTF_8)
        .lines
        .toVector

      assert(lines.exists(_.endsWith("/scalaparse_2.12-0.4.2.jar")))
      assert(lines.exists(_.endsWith("/scalaparse_2.12-0.4.2-sources.jar")))
  }

  "bootstrap" should "add standard and source JARs to the classpath with classloader isolation" in withFile() {

    (bootstrapFile, _) =>
      val repositoryOpt = RepositoryOptions(repository = List("bintray:scalameta/maven"))
      val artifactOptions = ArtifactOptions(
        sources = true,
        default = Some(true)
      )
      val common = CommonOptions(
        repositoryOptions = repositoryOpt
      )
      val isolatedLoaderOptions = IsolatedLoaderOptions(
        isolateTarget = List("foo"),
        isolated = List("foo:org.scalameta:trees_2.12:1.7.0")
      )
      val bootstrapSpecificOptions = BootstrapSpecificOptions(
        output = bootstrapFile.getPath,
        isolated = isolatedLoaderOptions,
        force = true,
        common = common
      )
      val bootstrapOptions = BootstrapOptions(artifactOptions, bootstrapSpecificOptions)

      Bootstrap.bootstrap(
        bootstrapOptions,
        RemainingArgs(Seq("com.geirsson:scalafmt-cli_2.12:1.4.0"), Seq())
      )

      def zis = new ZipInputStream(new ByteArrayInputStream(actualContent(bootstrapFile)))

      val fooLines = new String(zipEntryContent(zis, "bootstrap-isolation-foo-jar-urls"), UTF_8).lines.toVector
      val lines = new String(zipEntryContent(zis, "bootstrap-jar-urls"), UTF_8).lines.toVector

      assert(fooLines.exists(_.endsWith("/scalaparse_2.12-0.4.2.jar")))
      assert(fooLines.exists(_.endsWith("/scalaparse_2.12-0.4.2-sources.jar")))
      assert(!lines.exists(_.endsWith("/scalaparse_2.12-0.4.2.jar")))
      assert(!lines.exists(_.endsWith("/scalaparse_2.12-0.4.2-sources.jar")))

      assert(!fooLines.exists(_.endsWith("/scalameta_2.12-1.7.0.jar")))
      assert(!fooLines.exists(_.endsWith("/scalameta_2.12-1.7.0-sources.jar")))
      assert(lines.exists(_.endsWith("/scalameta_2.12-1.7.0.jar")))
      assert(lines.exists(_.endsWith("/scalameta_2.12-1.7.0-sources.jar")))
  }

  "bootstrap" should "be deterministic when deterministic option is specified" in
    withFile() {(bootstrapFile, _) =>
      withFile() {(bootstrapFile2, _) =>
        val repositoryOpt = RepositoryOptions(repository = List("bintray:scalameta/maven"))
        val artifactOptions = ArtifactOptions(
          sources = true,
          default = Some(true)
        )
        val common = CommonOptions(
          repositoryOptions = repositoryOpt
        )
        val isolatedLoaderOptions = IsolatedLoaderOptions(
          isolateTarget = List("foo"),
          isolated = List("foo:org.scalameta:trees_2.12:1.7.0")
        )
        val bootstrapSpecificOptions = BootstrapSpecificOptions(
          output = bootstrapFile.getPath,
          isolated = isolatedLoaderOptions,
          force = true,
          common = common,
          deterministic = true
        )
        val bootstrapOptions = BootstrapOptions(artifactOptions, bootstrapSpecificOptions)
        Bootstrap.bootstrap(
          bootstrapOptions,
          RemainingArgs(Seq("com.geirsson:scalafmt-cli_2.12:1.4.0"), Seq())
        )

        //We need to wait between two runs to ensure we don't accidentally get the same hash
        Thread.sleep(2000)

        val bootstrapSpecificOptions2 = bootstrapSpecificOptions.copy(
          output = bootstrapFile2.getPath
        )
        val bootstrapOptions2 = BootstrapOptions(artifactOptions, bootstrapSpecificOptions2)
        Bootstrap.bootstrap(
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

  "bootstrap" should "rename JAR with the same file name" in withFile() {

    (bootstrapFile, _) =>
      val repositoryOpt = RepositoryOptions(repository = List("bintray:scalacenter/releases"))
      val common = CommonOptions(
        repositoryOptions = repositoryOpt
      )
      val bootstrapSpecificOptions = BootstrapSpecificOptions(
        output = bootstrapFile.getPath,
        force = true,
        standalone = true,
        common = common
      )
      val bootstrapOptions = BootstrapOptions(options = bootstrapSpecificOptions)

      Bootstrap.bootstrap(
        bootstrapOptions,
        RemainingArgs(Seq("org.scalameta:metals_2.12:0.2.0"), Seq())
      )

      val zis = new ZipInputStream(new ByteArrayInputStream(actualContent(bootstrapFile)))

      val lines = new String(zipEntryContent(zis, "bootstrap-jar-resources"), UTF_8)
        .lines
        .toVector

      val fastparseLines = lines.filter(_.contains("/fastparse_2.12-1.0.0"))
      val fastparseUtilsLines = lines.filter(_.contains("/fastparse-utils_2.12-1.0.0"))

      assert(fastparseLines.length == 2)
      assert(fastparseLines.distinct.length == 2)
      assert(fastparseUtilsLines.length == 2)
      assert(fastparseUtilsLines.distinct.length == 2)
  }
}
