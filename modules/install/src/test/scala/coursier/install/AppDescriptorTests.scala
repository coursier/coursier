package coursier.install

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, FileSystemException, Path}

import coursier.cache.{Cache, MockCache}
import coursier.core.Repository
import coursier.maven.MavenRepository
import coursier.parse.{DependencyParser, JavaOrScalaDependency}
import coursier.testcache.TestCache
import coursier.util.Task
import utest._

import scala.jdk.CollectionConverters._
import scala.util.Properties

object AppDescriptorTests extends TestSuite {

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
      Files.deleteIfExists(d)
      ()
    }
    else
      try Files.deleteIfExists(d)
      catch {
        case e: FileSystemException if Properties.isWin =>
          System.err.println(s"Ignored error while deleting temporary file $d: $e")
      }

  private def withTempDir[T](f: Path => T): T = {
    val tmpDir = Files.createTempDirectory("coursier-app-descriptor-test")
    try f(tmpDir)
    finally delete(tmpDir)
  }

  private def write(path: Path, content: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    ()
  }

  private def orgDir(repoDir: Path, org: String): Path =
    org.split('.').foldLeft(repoDir)(_.resolve(_))

  /** Writes a maven-metadata.xml listing `versions` for the passed module */
  private def writeVersions(
    repoDir: Path,
    org: String,
    name: String,
    versions: Seq[String]
  ): Unit = {
    val versionElems = versions
      .map(v => s"      <version>$v</version>")
      .mkString("\n")
    write(
      orgDir(repoDir, org).resolve(name).resolve("maven-metadata.xml"),
      s"""<?xml version="1.0" encoding="UTF-8"?>
         |<metadata>
         |  <groupId>$org</groupId>
         |  <artifactId>$name</artifactId>
         |  <versioning>
         |    <latest>${versions.last}</latest>
         |    <release>${versions.last}</release>
         |    <versions>
         |$versionElems
         |    </versions>
         |    <lastUpdated>20250101000000</lastUpdated>
         |  </versioning>
         |</metadata>
         |""".stripMargin
    )
  }

  /** Writes the directory listings the Complete API relies on, for the whole repository
    *
    * Without those, the Complete API cannot list the modules of an organization, like when a module
    * was published after the last index refresh of the repository.
    */
  private def writeDirectoryListings(dir: Path): Unit = {
    val subDirs = {
      var s: java.util.stream.Stream[Path] = null
      try {
        s = Files.list(dir)
        s.iterator()
          .asScala
          .filter(Files.isDirectory(_))
          .map(_.getFileName.toString)
          .toVector
      }
      finally if (s != null)
          s.close()
    }
    val links = subDirs
      .sorted
      .map(name => s"""<li><a href="$name/">$name/</a></li>""")
      .mkString("\n")
    write(
      dir.resolve(".directory"),
      s"""<!DOCTYPE html>
         |<html>
         |<head></head>
         |<body>
         |<ul>
         |$links
         |</ul>
         |</body>
         |</html>
         |""".stripMargin
    )
    for (subDir <- subDirs)
      writeDirectoryListings(dir.resolve(subDir))
  }

  private val scalaLibraryVersions = Seq("2.12.20", "2.13.16")
  private val scala3LibraryVersions =
    Seq("3.3.6", "3.7.2", "3.8.0-RC1-bin-20250601-1234abc-NIGHTLY")

  /** Writes the metadata making [[scalaLibraryVersions]] and [[scala3LibraryVersions]] the Scala
    * versions available in the repository
    */
  private def initRepo(repoDir: Path): Unit = {
    writeVersions(repoDir, "org.scala-lang", "scala-library", scalaLibraryVersions)
    writeVersions(repoDir, "org.scala-lang", "scala3-library_3", scala3LibraryVersions)
  }

  private def cache(repoDir: Path): Cache[Task] =
    MockCache.create[Task](
      repoDir,
      pool = TestCache.pool,
      baseChangingOpt = None
    )

  private def repositories(repoDir: Path): Seq[Repository] =
    Seq(MavenRepository(repoDir.toUri.toASCIIString))

  private def dependency(input: String): JavaOrScalaDependency =
    DependencyParser.javaOrScalaDependencyParams(input) match {
      case Left(err)     => sys.error(s"Error parsing '$input': $err")
      case Right((d, _)) => d
    }

  private def descriptor(repoDir: Path, deps: String*): AppDescriptor =
    AppDescriptor()
      .copy(
        repositories = repositories(repoDir),
        dependencies = deps.map(dependency)
      )

  private def processDependencies(desc: AppDescriptor, repoDir: Path) =
    desc.processDependencies(cache(repoDir), None, verbosity = 0)

  /** The Scala version and the module names [[AppDescriptor.processDependencies]] settles on */
  private def scalaVersionAndModules(
    desc: AppDescriptor,
    repoDir: Path
  ): (Option[String], Seq[String]) =
    processDependencies(desc, repoDir) match {
      case Left(err) => throw err
      case Right((scalaVersionOpt, _, deps)) =>
        (scalaVersionOpt.map(_.asString), deps.map(_.module.name.value))
    }

  private def expectScalaDependenciesNotFound(
    res: Either[AppArtifacts.AppArtifactsException, _]
  ): Unit =
    res match {
      case Left(_: AppArtifacts.ScalaDependenciesNotFound) => ()
      case other => sys.error(s"Expected a ScalaDependenciesNotFound error, got $other")
    }

  val tests = Tests {

    test("scala 3 only module") {
      // the Complete API cannot list the modules of that organization (no directory listing
      // here), like when the module was published after the last index refresh
      test("no completions") {
        withTempDir { repoDir =>
          initRepo(repoDir)
          writeVersions(repoDir, "dev.capslock", "jpyc-qr-signboard_3", Seq("0.1.7"))

          val desc = descriptor(repoDir, "dev.capslock::jpyc-qr-signboard:0.1.7")
          val (scalaVersionOpt, moduleNames) = scalaVersionAndModules(desc, repoDir)

          assert(scalaVersionOpt == Some("3.7.2"))
          assert(moduleNames == Seq("jpyc-qr-signboard_3"))
        }
      }

      test("completions") {
        withTempDir { repoDir =>
          initRepo(repoDir)
          writeVersions(repoDir, "dev.capslock", "jpyc-qr-signboard_3", Seq("0.1.7"))
          writeDirectoryListings(repoDir)

          val desc = descriptor(repoDir, "dev.capslock::jpyc-qr-signboard:0.1.7")
          val (scalaVersionOpt, moduleNames) = scalaVersionAndModules(desc, repoDir)

          assert(scalaVersionOpt == Some("3.7.2"))
          assert(moduleNames == Seq("jpyc-qr-signboard_3"))
        }
      }
    }

    // without completions, we fall back to trying all known Scala versions - the Scala 3 ones
    // must be ruled out here, as this module has no Scala 3 variant
    test("scala 2.13 only module") {
      test("no completions") {
        withTempDir { repoDir =>
          initRepo(repoDir)
          writeVersions(repoDir, "com.example", "lib_2.13", Seq("1.0.0"))

          val desc                           = descriptor(repoDir, "com.example::lib:1.0.0")
          val (scalaVersionOpt, moduleNames) = scalaVersionAndModules(desc, repoDir)

          assert(scalaVersionOpt == Some("2.13.16"))
          assert(moduleNames == Seq("lib_2.13"))
        }
      }

      test("completions") {
        withTempDir { repoDir =>
          initRepo(repoDir)
          writeVersions(repoDir, "com.example", "lib_2.13", Seq("1.0.0"))
          writeDirectoryListings(repoDir)

          val desc                           = descriptor(repoDir, "com.example::lib:1.0.0")
          val (scalaVersionOpt, moduleNames) = scalaVersionAndModules(desc, repoDir)

          assert(scalaVersionOpt == Some("2.13.16"))
          assert(moduleNames == Seq("lib_2.13"))
        }
      }
    }

    test("cross-published module") {
      def check(withListings: Boolean) =
        withTempDir { repoDir =>
          initRepo(repoDir)
          writeVersions(repoDir, "com.example", "lib_2.13", Seq("1.0.0"))
          writeVersions(repoDir, "com.example", "lib_3", Seq("1.0.0"))
          if (withListings)
            writeDirectoryListings(repoDir)

          val desc                           = descriptor(repoDir, "com.example::lib:1.0.0")
          val (scalaVersionOpt, moduleNames) = scalaVersionAndModules(desc, repoDir)

          assert(scalaVersionOpt == Some("3.7.2"))
          assert(moduleNames == Seq("lib_3"))
        }

      test("no completions") - check(withListings = false)
      test("completions") - check(withListings = true)
    }

    test("full cross version module") {
      def check(withListings: Boolean) =
        withTempDir { repoDir =>
          initRepo(repoDir)
          writeVersions(repoDir, "com.example", "plugin_3.3.6", Seq("1.0.0"))
          if (withListings)
            writeDirectoryListings(repoDir)

          val desc                           = descriptor(repoDir, "com.example:::plugin:1.0.0")
          val (scalaVersionOpt, moduleNames) = scalaVersionAndModules(desc, repoDir)

          assert(scalaVersionOpt == Some("3.3.6"))
          assert(moduleNames == Seq("plugin_3.3.6"))
        }

      test("no completions") - check(withListings = false)
      test("completions") - check(withListings = true)
    }

    test("non existing module") {
      withTempDir { repoDir =>
        initRepo(repoDir)

        val desc = descriptor(repoDir, "com.example::does-not-exist:1.0.0")
        expectScalaDependenciesNotFound(processDependencies(desc, repoDir))
      }
    }

    // the fallback must not make us pick a Scala version only some of the modules are available
    // for
    test("no common scala version") {
      withTempDir { repoDir =>
        initRepo(repoDir)
        writeVersions(repoDir, "com.example", "lib_2.13", Seq("1.0.0"))
        writeVersions(repoDir, "com.example", "other-lib_3", Seq("1.0.0"))

        val desc = descriptor(repoDir, "com.example::lib:1.0.0", "com.example::other-lib:1.0.0")
        expectScalaDependenciesNotFound(processDependencies(desc, repoDir))
      }
    }
  }
}
