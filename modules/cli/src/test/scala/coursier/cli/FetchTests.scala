package coursier.cli

import java.io._
import java.net.URLEncoder.encode

import caseapp.core.RemainingArgs
import coursier.cli.options._
import coursier.cli.options._
import java.io._
import java.net.URLClassLoader
import java.net.URLEncoder.encode
import java.nio.charset.StandardCharsets
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}

import cats.data.Validated
import coursier.cache.FileCache
import coursier.cli.fetch.{Fetch, FetchOptions, FetchParams}
import coursier.cli.launch.Launch
import coursier.cli.resolve.{ResolveException, SharedResolveOptions}
import coursier.cli.TestUtil.{mayThrow, withFile, withTempDir}
import coursier.install.MainClass
import coursier.util.Sync
import utest._

import scala.concurrent.ExecutionContext
import scala.io.Source

object FetchTests extends TestSuite {

  def checkPath(file: Option[String], path: String): Unit =
    assert(file.exists(_.contains(path.replace("/", File.separator))))

  val pool = Sync.fixedThreadPool(6)
  val ec   = ExecutionContext.fromExecutorService(pool)

  def paramsOrThrow(options: FetchOptions): FetchParams =
    FetchParams(options) match {
      case Validated.Invalid(errors) =>
        sys.error("Got errors:" + System.lineSeparator() + errors.toList.map(e =>
          s"  $e" + System.lineSeparator()
        ).mkString)
      case Validated.Valid(params0) =>
        params0
    }

  val tests = Tests {
    test("get all files") {
      val options = FetchOptions()
      val params  = paramsOrThrow(options)
      val (_, _, _, files) = Fetch.task(params, pool, Seq("junit:junit:4.12"))
        .unsafeRun(wrapExceptions = true)(ec)
      assert(files.map(_._2.getName).toSet.equals(Set("junit-4.12.jar", "hamcrest-core-1.3.jar")))
    }

    test("fetch default files") {
      val artifactOpt = ArtifactOptions(
        classifier = List("_")
      )
      val options = FetchOptions(artifactOptions = artifactOpt)
      val params  = paramsOrThrow(options)
      val (_, _, _, files) = Fetch.task(params, pool, Seq("junit:junit:4.12"))
        .unsafeRun(wrapExceptions = true)(ec)
      assert(files.map(_._2.getName).toSet.equals(Set("junit-4.12.jar", "hamcrest-core-1.3.jar")))
    }

    test("fetch dependencies from file") {
      withFile(
        "junit:junit:4.12"
      ) { (file, writer) =>
        val dependencyOpt = DependencyOptions(dependencyFile = List(file.getAbsolutePath))
        val resolveOpt    = SharedResolveOptions(dependencyOptions = dependencyOpt)
        val options       = FetchOptions(resolveOptions = resolveOpt)
        val params        = paramsOrThrow(options)

        val (_, _, _, files) = Fetch.task(params, pool, Seq.empty)
          .unsafeRun(wrapExceptions = true)(ec)

        assert(files.map(_._2.getName).toSet.equals(Set("junit-4.12.jar", "hamcrest-core-1.3.jar")))
      }
    }

    test("fail fetching dependencies from file with invalid content") {
      withFile(
        "junit:junit:4.12, something_else"
      ) { (file, writer) =>
        val dependencyOpt = DependencyOptions(dependencyFile = List(file.getAbsolutePath))
        val resolveOpt    = SharedResolveOptions(dependencyOptions = dependencyOpt)
        val options       = FetchOptions(resolveOptions = resolveOpt)
        val params        = paramsOrThrow(options)

        intercept[ResolveException] {
          Fetch.task(params, pool, Seq.empty).unsafeRun()(ec)
        }
      }
    }

    test("fail fetching dependencies from non-existing file") {
      val path          = "non-existing-file-path"
      val dependencyOpt = DependencyOptions(dependencyFile = List(path))
      val resolveOpt    = SharedResolveOptions(dependencyOptions = dependencyOpt)
      val options       = FetchOptions(resolveOptions = resolveOpt)

      val expectedErrorMessage = s"Error reading dependencies from $path"
      val thrownException = intercept[Exception] {
        paramsOrThrow(options)
      }
      assert(thrownException.getMessage == expectedErrorMessage)
    }

    test("Underscore and source classifier should fetch default and source files") {
      val artifactOpt = ArtifactOptions(
        classifier = List("_"),
        sources = true
      )
      val options = FetchOptions(artifactOptions = artifactOpt)
      val params  = paramsOrThrow(options)
      val (_, _, _, files) = Fetch.task(params, pool, Seq("junit:junit:4.12"))
        .unsafeRun(wrapExceptions = true)(ec)
      assert(files.map(_._2.getName).toSet.equals(Set(
        "junit-4.12.jar",
        "junit-4.12-sources.jar",
        "hamcrest-core-1.3.jar",
        "hamcrest-core-1.3-sources.jar"
      )))
    }

    test("Default and source options should fetch default and source files") {
      val artifactOpt = ArtifactOptions(
        default = Some(true),
        sources = true
      )
      val options = FetchOptions(
        artifactOptions = artifactOpt
      )
      val params = paramsOrThrow(options)
      val (_, _, _, files) = Fetch.task(params, pool, Seq("junit:junit:4.12"))
        .unsafeRun(wrapExceptions = true)(ec)
      assert(files.map(_._2.getName).toSet.equals(Set(
        "junit-4.12.jar",
        "junit-4.12-sources.jar",
        "hamcrest-core-1.3.jar",
        "hamcrest-core-1.3-sources.jar"
      )))
    }

    test("scalafmt-cli fetch should discover all main classes") {
      val options = FetchOptions()
      val params  = paramsOrThrow(options)
      val (_, _, _, files) = Fetch.task(params, pool, Seq("com.geirsson:scalafmt-cli_2.12:1.4.0"))
        .unsafeRun(wrapExceptions = true)(ec)
      assert(MainClass.mainClasses(files.map(_._2)) == Map(
        ("", "")                -> "com.martiansoftware.nailgun.NGServer",
        ("com.geirsson", "cli") -> "org.scalafmt.cli.Cli"
      ))
    }

    test("scalafix-cli fetch should discover all main classes") {
      val options = FetchOptions()
      val params  = paramsOrThrow(options)
      val (_, _, _, files) =
        Fetch.task(params, pool, Seq("ch.epfl.scala:scalafix-cli_2.12.4:0.5.10"))
          .unsafeRun(wrapExceptions = true)(ec)
      val loader = new URLClassLoader(files.map(_._2.toURI.toURL).toArray, Launch.baseLoader)
      assert(MainClass.mainClasses(files.map(_._2)) == Map(
        ("", "")                 -> "com.martiansoftware.nailgun.NGServer",
        ("ch.epfl.scala", "cli") -> "scalafix.cli.Cli"
      ))
    }

    test("ammonite fetch should discover all main classes") {
      val options = FetchOptions()
      val params  = paramsOrThrow(options)
      val (_, _, _, files) = Fetch.task(params, pool, Seq("com.lihaoyi:ammonite_2.12.4:1.1.0"))
        .unsafeRun(wrapExceptions = true)(ec)
      val loader = new URLClassLoader(files.map(_._2.toURI.toURL).toArray, Launch.baseLoader)
      assert(MainClass.mainClasses(files.map(_._2)) == Map(
        ("", "Javassist")                -> "javassist.CtClass",
        ("", "Java Native Access (JNA)") -> "com.sun.jna.Native",
        ("com.lihaoyi", "ammonite")      -> "ammonite.Main"
      ))
    }

    test("sssio fetch should discover all main classes") {
      val options = FetchOptions()
      val params  = paramsOrThrow(options)
      val (_, _, _, files) = Fetch.task(params, pool, Seq("lt.dvim.sssio:sssio_2.12:0.0.1"))
        .unsafeRun(wrapExceptions = true)(ec)
      val loader = new URLClassLoader(files.map(_._2.toURI.toURL).toArray, Launch.baseLoader)
      assert(MainClass.mainClasses(files.map(_._2)) == Map(
        ("", "")                   -> "com.kenai.jffi.Main",
        ("lt.dvim.sssio", "sssio") -> "lt.dvim.sssio.Sssio"
      ))
    }

    /* Result:
     * |└─ a:b:c
     */
    test(
      "local file dep url should have coursier-fetch-test.jar and cached for second run"
    ) {
      withFile() {
        (jsonFile, _) =>
          withFile("tada", "coursier-fetch-test", ".jar") {
            (testFile, _) =>
              val testFileUri: String = testFile.toURI.toASCIIString
              val encodedUrl: String  = encode(testFileUri, "UTF-8")

              val cacheOpt   = CacheOptions(cacheFileArtifacts = true)
              val resolveOpt = SharedResolveOptions(cacheOptions = cacheOpt)
              val options    = FetchOptions(resolveOptions = resolveOpt)
              val params     = paramsOrThrow(options)

              // fetch with encoded url set to temp jar
              val task = Fetch.task(
                params,
                pool,
                Seq(
                  "a:b:c,url=" + encodedUrl
                )
              )
              val (_, _, _, artifactFiles) = task.unsafeRun(wrapExceptions = true)(ec)

              val file = artifactFiles.map(_._2) match {
                case Seq(f) => f
                case _      => sys.error("Expected a single artifact")
              }

              // open jar and inspect contents
              val fileContents1 = Source.fromFile(file).getLines().mkString
              assert(fileContents1 == "tada")

              file.delete()

              val (_, _, _, artifactFiles0) = task.unsafeRun(wrapExceptions = true)(ec)
              val testFile0 = artifactFiles0.map(_._2) match {
                case Seq(f) => f
                case _      => sys.error("Expected a single artifact")
              }

              val inCoursierCache =
                testFile0.toString.contains("/coursier/") || // Linux
                testFile0.toString.contains("/Coursier/") || // macOS
                testFile0.toString.contains("\\Coursier\\")  // Windows?
              assert(inCoursierCache && testFile0.toString.contains(testFile.getName))
          }
      }
    }

    /* Result: Error
     */
    test("external dep url with forced version should throw an error") {
      withFile() {
        (_, _) =>
          val resolutionOpt =
            ResolutionOptions(forceVersion = List("org.apache.commons:commons-compress:1.4.1"))
          val resolveOpt = SharedResolveOptions(
            resolutionOptions = resolutionOpt
          )
          val options = FetchOptions(resolveOptions = resolveOpt)
          val params  = paramsOrThrow(options)

          val externalUrl =
            encode("https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar", "UTF-8")

          val thrownException =
            try {
              Fetch.task(
                params,
                pool,
                Seq(
                  "org.apache.commons:commons-compress:1.5,url=" + externalUrl
                )
              ).unsafeRun(wrapExceptions = true)(ec)
              false
            }
            catch {
              case _: Exception =>
                true
            }
          assert(thrownException)
      }
    }

    /* Result:
     * |└─ org.apache.commons:commons-compress:1.4.1 -> 1.5
     */
    test("external dep url on higher version should fetch junit-4.12.jar") {
      withFile() {
        (_, _) =>
          val params = paramsOrThrow(FetchOptions())

          // encode path to different jar than requested
          val externalUrl =
            encode("https://repo1.maven.org/maven2/junit/junit/4.12/junit-4.12.jar", "UTF-8")

          val (_, _, _, artifactFiles) = Fetch.task(
            params,
            pool,
            Seq(
              "org.apache.commons:commons-compress:1.4.1",
              "org.apache.commons:commons-compress:1.5,url=" + externalUrl
            )
          ).unsafeRun(wrapExceptions = true)(ec)

          val files = artifactFiles.map(_._2)
          assert(files.length == 1)
          assert(
            files
              .head
              .toString
              .endsWith("junit/junit/4.12/junit-4.12.jar".replace("/", File.separator))
          )
      }
    }

    test("Bad pom resolve should succeed with retry") {
      withTempDir("tmp_dir") {
        dir =>
          def runFetchJunit() = {
            val cacheOpt   = CacheOptions(cache = Some(dir.getAbsolutePath))
            val resolveOpt = SharedResolveOptions(cacheOptions = cacheOpt)
            val options    = FetchOptions(resolveOptions = resolveOpt)
            val params     = paramsOrThrow(options)
            val (_, _, _, files) = mayThrow {
              Fetch.task(params, pool, Seq("junit:junit:4.12"))
                .unsafeRun(wrapExceptions = true)(ec)
            }
            assert(files.map(_._2.getName).toSet
              .equals(Set("junit-4.12.jar", "hamcrest-core-1.3.jar")))
            val junitJarPath =
              files.map(_._2.getAbsolutePath()).filter(_.contains("junit-4.12.jar"))
                .head
            val junitPomFile    = Paths.get(junitJarPath.replace(".jar", ".pom"))
            val junitPomShaFile = Paths.get(junitJarPath.replace(".jar", ".pom.sha1"))
            val junitAlternativePomShaFile =
              FileCache.auxiliaryFile(junitPomFile.toFile, "SHA-1").toPath
            assert(Files.isRegularFile(junitPomFile))
            assert(Files.isRegularFile(junitPomShaFile) || Files.isRegularFile(
              junitAlternativePomShaFile
            )) // , s"Found ${junitPomShaFile.getParent.toFile.list.toSeq.sorted}")
            junitPomFile
          }

          val junitPomFile       = runFetchJunit()
          val originalPomContent = Files.readAllBytes(junitPomFile)

          // Corrupt the pom content
          Files.write(junitPomFile, "bad pom".getBytes(UTF_8))

          // Corrupt the content of the calculated pom checksum
          val storedDigestFile =
            FileCache.auxiliaryFile(junitPomFile.toFile, "SHA-1.computed").toPath
          Files.write(storedDigestFile, Array[Byte](1, 2, 3))

          // Run fetch again and it should pass because of retrying om the bad pom.
          val pom = runFetchJunit()
          assert(Files.readAllBytes(pom).sameElements(originalPomContent))
      }
    }

    test("Bad pom sha-1 resolve should succeed with retry") {
      withTempDir("tmp_dir") {
        dir =>
          def runFetchJunit() = {
            val cacheOpt   = CacheOptions(cache = Some(dir.getAbsolutePath))
            val resolveOpt = SharedResolveOptions(cacheOptions = cacheOpt)
            val options    = FetchOptions(resolveOptions = resolveOpt)
            val params     = paramsOrThrow(options)
            val (_, _, _, files) = Fetch.task(params, pool, Seq("junit:junit:4.12"))
              .unsafeRun(wrapExceptions = true)(ec)
            assert(files.map(_._2.getName).toSet
              .equals(Set("junit-4.12.jar", "hamcrest-core-1.3.jar")))
            val junitJarPath =
              files.map(_._2.getAbsolutePath()).filter(_.contains("junit-4.12.jar"))
                .head
            val junitPomFile    = Paths.get(junitJarPath.replace(".jar", ".pom"))
            val junitPomShaFile = Paths.get(junitJarPath.replace(".jar", ".pom.sha1"))
            val junitAlternativePomShaFile =
              FileCache.auxiliaryFile(junitPomFile.toFile, "SHA-1").toPath
            assert(Files.isRegularFile(junitPomFile))
            assert(Files.isRegularFile(junitPomShaFile) || Files.isRegularFile(
              junitAlternativePomShaFile
            ))
            if (Files.isRegularFile(junitPomShaFile))
              junitPomShaFile
            else if (Files.isRegularFile(junitAlternativePomShaFile))
              junitAlternativePomShaFile
            else
              sys.error(s"Neither $junitPomShaFile nor $junitAlternativePomShaFile found")
          }

          val junitPomSha1File   = runFetchJunit()
          val originalShaContent = Files.readAllBytes(junitPomSha1File)

          // Corrupt the pom content
          System.err.println(s"Corrupting $junitPomSha1File")
          Files.write(junitPomSha1File, "adc83b19e793491b1c6ea0fd8b46cd9f32e592fc".getBytes(UTF_8))

          // Run fetch again and it should pass because of retrying om the bad pom.
          val sha = runFetchJunit()
          assert(Files.readAllBytes(sha).sameElements(originalShaContent))
      }
    }

    test("Bad jar resolve should succeed with retry") {
      withTempDir("tmp_dir") {
        dir =>
          def runFetchJunit() = {
            val cacheOpt   = CacheOptions(cache = Some(dir.getAbsolutePath))
            val resolveOpt = SharedResolveOptions(cacheOptions = cacheOpt)
            val options    = FetchOptions(resolveOptions = resolveOpt)
            val params     = paramsOrThrow(options)
            val (_, _, _, files) = Fetch.task(params, pool, Seq("junit:junit:4.12"))
              .unsafeRun(wrapExceptions = true)(ec)
            assert(files.map(_._2.getName).toSet
              .equals(Set("junit-4.12.jar", "hamcrest-core-1.3.jar")))
            val junitJarPath =
              files.map(_._2.getAbsolutePath()).filter(_.contains("junit-4.12.jar"))
                .head
            Paths.get(junitJarPath)
          }

          val originalJunitJar        = runFetchJunit()
          val originalJunitJarContent = Files.readAllBytes(originalJunitJar)

          // Corrupt the jar content
          Files.write(originalJunitJar, "bad jar".getBytes(UTF_8))

          // Corrupt the content of the calculated jar checksum
          val storedDigestFile =
            FileCache.auxiliaryFile(originalJunitJar.toFile, "SHA-1.computed").toPath
          Files.write(storedDigestFile, Array[Byte](1, 2, 3))

          // Run fetch again and it should pass because of retrying on the bad jar.
          val jar = runFetchJunit()
          assert(Files.readAllBytes(jar).sameElements(originalJunitJarContent))
      }
    }

    test("Bad jar sha-1 resolve should succeed with retry") {
      withTempDir("tmp_dir") {
        dir =>
          def runFetchJunit() = {
            val cacheOpt   = CacheOptions(cache = Some(dir.getAbsolutePath))
            val resolveOpt = SharedResolveOptions(cacheOptions = cacheOpt)
            val options    = FetchOptions(resolveOptions = resolveOpt)
            val params     = paramsOrThrow(options)
            val (_, _, _, files) = Fetch.task(params, pool, Seq("junit:junit:4.12"))
              .unsafeRun(wrapExceptions = true)(ec)
            assert(files.map(_._2.getName).toSet
              .equals(Set("junit-4.12.jar", "hamcrest-core-1.3.jar")))
            val junitJarPath =
              files.map(_._2.getAbsolutePath()).filter(_.contains("junit-4.12.jar"))
                .head
            val junitJarShaFile = Paths.get(junitJarPath.replace(".jar", ".jar.sha1"))
            val junitAlternativePomShaFile =
              FileCache.auxiliaryFile(new File(junitJarPath), "SHA-1").toPath
            if (Files.isRegularFile(junitJarShaFile))
              junitJarShaFile
            else if (Files.isRegularFile(junitAlternativePomShaFile))
              junitAlternativePomShaFile
            else
              sys.error(s"Neither $junitJarShaFile nor $junitAlternativePomShaFile found")
          }

          val originalJunitJarSha1        = runFetchJunit()
          val originalJunitJarSha1Content = Files.readAllBytes(originalJunitJarSha1)

          // Corrupt the jar content
          System.err.println(s"Corrupting $originalJunitJarSha1")
          Files.write(
            originalJunitJarSha1,
            "adc83b19e793491b1c6ea0fd8b46cd9f32e592fc".getBytes(UTF_8)
          )

          // Run fetch again and it should pass because of retrying on the bad jar.
          val jarSha1 = runFetchJunit()
          assert(Files.readAllBytes(jarSha1).sameElements(originalJunitJarSha1Content))
      }
    }

    test("Wrong range partial artifact resolve should succeed with retry") {
      withTempDir(
        "tmp_dir"
      ) {
        dir =>
          def runFetchJunit() = {
            val cacheOpt   = CacheOptions(mode = "force", cache = Some(dir.getAbsolutePath))
            val resolveOpt = SharedResolveOptions(cacheOptions = cacheOpt)
            val options    = FetchOptions(resolveOptions = resolveOpt)
            val params     = paramsOrThrow(options)
            val (_, _, _, files) = Fetch.task(params, pool, Seq("junit:junit:4.6"))
              .unsafeRun(wrapExceptions = true)(ec)
            assert(files.map(_._2.getName).toSet
              .equals(Set("junit-4.6.jar")))
            val junitJarPath = files.map(_._2.getAbsolutePath()).filter(_.contains("junit-4.6.jar"))
              .head
            Paths.get(junitJarPath)
          }

          val originalJunitJar = runFetchJunit()

          val originalJunitJarContent = Files.readAllBytes(originalJunitJar)

          // Move the jar to partial (but complete) download
          val newJunitJar =
            originalJunitJar.getParent.resolve(originalJunitJar.getFileName.toString + ".part")
          Files.move(originalJunitJar, newJunitJar)

          // Run fetch again and it should pass because of retrying on the partial jar.
          val jar = runFetchJunit()
          assert(Files.readAllBytes(jar).sameElements(originalJunitJarContent))
      }
    }

    test("fail because of resolution") {
      val options = FetchOptions()
      val params  = paramsOrThrow(options)
      val a = Fetch.task(params, pool, Seq("sh.almond:scala-kernel_2.12.8:0.2.2"))
        .attempt
        .unsafeRun(wrapExceptions = true)(ec)

      a match {
        case Right(_) =>
          throw new Exception("should have failed")
        case Left(_: ResolveException) =>
        case Left(ex) =>
          throw new Exception("Unexpected exception type", ex)
      }
    }

    test("fail to resolve, but try to fetch artifacts anyway") {
      val artifactOptions = ArtifactOptions(
        forceFetch = true
      )
      val options = FetchOptions(
        artifactOptions = artifactOptions
      )
      val params = paramsOrThrow(options)
      val (_, _, _, l) = Fetch.task(params, pool, Seq("sh.almond:scala-kernel_2.12.8:0.2.2"))
        .unsafeRun(wrapExceptions = true)(ec)

      val expectedUrls = Seq(
        "co/fs2/fs2-core_2.12/0.10.7/fs2-core_2.12-0.10.7.jar",
        "com/chuusai/shapeless_2.12/2.3.3/shapeless_2.12-2.3.3.jar",
        "com/fasterxml/jackson/core/jackson-annotations/2.8.0/jackson-annotations-2.8.0.jar",
        "com/fasterxml/jackson/core/jackson-core/2.8.4/jackson-core-2.8.4.jar",
        "com/fasterxml/jackson/core/jackson-databind/2.8.4/jackson-databind-2.8.4.jar",
        "com/github/alexarchambault/argonaut-shapeless_6.2_2.12/1.2.0-M9/argonaut-shapeless_6.2_2.12-1.2.0-M9.jar",
        "com/github/alexarchambault/case-app-annotations_2.12/2.0.0-M5/case-app-annotations_2.12-2.0.0-M5.jar",
        "com/github/alexarchambault/case-app-util_2.12/2.0.0-M5/case-app-util_2.12-2.0.0-M5.jar",
        "com/github/alexarchambault/case-app_2.12/2.0.0-M5/case-app_2.12-2.0.0-M5.jar",
        "com/github/javaparser/javaparser-core/3.2.5/javaparser-core-3.2.5.jar",
        "com/github/pathikrit/better-files_2.12/3.6.0/better-files_2.12-3.6.0.jar",
        "com/github/scopt/scopt_2.12/3.5.0/scopt_2.12-3.5.0.jar",
        "com/google/protobuf/protobuf-java/3.6.0/protobuf-java-3.6.0.jar",
        "com/lihaoyi/acyclic_2.12/0.1.5/acyclic_2.12-0.1.5.jar",
        "com/lihaoyi/ammonite-interp_2.12.8/1.5.0-4-6296f20/ammonite-interp_2.12.8-1.5.0-4-6296f20.jar",
        "com/lihaoyi/ammonite-ops_2.12/1.5.0-4-6296f20/ammonite-ops_2.12-1.5.0-4-6296f20.jar",
        "com/lihaoyi/ammonite-repl_2.12.8/1.5.0-4-6296f20/ammonite-repl_2.12.8-1.5.0-4-6296f20.jar",
        "com/lihaoyi/ammonite-runtime_2.12/1.5.0-4-6296f20/ammonite-runtime_2.12-1.5.0-4-6296f20.jar",
        "com/lihaoyi/ammonite-terminal_2.12/1.5.0-4-6296f20/ammonite-terminal_2.12-1.5.0-4-6296f20.jar",
        "com/lihaoyi/ammonite-util_2.12/1.5.0-4-6296f20/ammonite-util_2.12-1.5.0-4-6296f20.jar",
        "com/lihaoyi/fansi_2.12/0.2.5/fansi_2.12-0.2.5.jar",
        "com/lihaoyi/fastparse_2.12/2.0.5/fastparse_2.12-2.0.5.jar",
        "com/lihaoyi/geny_2.12/0.1.5/geny_2.12-0.1.5.jar",
        "com/lihaoyi/os-lib_2.12/0.2.6/os-lib_2.12-0.2.6.jar",
        "com/lihaoyi/pprint_2.12/0.5.3/pprint_2.12-0.5.3.jar",
        "com/lihaoyi/scalaparse_2.12/2.0.5/scalaparse_2.12-2.0.5.jar",
        "com/lihaoyi/scalatags_2.12/0.6.7/scalatags_2.12-0.6.7.jar",
        "com/lihaoyi/sourcecode_2.12/0.1.5/sourcecode_2.12-0.1.5.jar",
        "com/lihaoyi/ujson_2.12/0.7.1/ujson_2.12-0.7.1.jar",
        "com/lihaoyi/upack_2.12/0.7.1/upack_2.12-0.7.1.jar",
        "com/lihaoyi/upickle-core_2.12/0.7.1/upickle-core_2.12-0.7.1.jar",
        "com/lihaoyi/upickle-implicits_2.12/0.7.1/upickle-implicits_2.12-0.7.1.jar",
        "com/lihaoyi/upickle_2.12/0.7.1/upickle_2.12-0.7.1.jar",
        "com/lihaoyi/utest_2.12/0.6.4/utest_2.12-0.6.4.jar",
        "com/thesamet/scalapb/lenses_2.12/0.8.0/lenses_2.12-0.8.0.jar",
        "com/thesamet/scalapb/scalapb-json4s_2.12/0.7.1/scalapb-json4s_2.12-0.7.1.jar",
        "com/thesamet/scalapb/scalapb-runtime_2.12/0.8.0/scalapb-runtime_2.12-0.8.0.jar",
        "com/thoughtworks/paranamer/paranamer/2.8/paranamer-2.8.jar",
        "com/thoughtworks/qdox/qdox/2.0-M9/qdox-2.0-M9.jar",
        "io/argonaut/argonaut_2.12/6.2.2/argonaut_2.12-6.2.2.jar",
        "io/get-coursier/coursier-cache_2.12/1.1.0-M7/coursier-cache_2.12-1.1.0-M7.jar",
        "io/get-coursier/coursier_2.12/1.1.0-M7/coursier_2.12-1.1.0-M7.jar",
        "io/github/soc/directories/11/directories-11.jar",
        "io/undertow/undertow-core/2.0.13.Final/undertow-core-2.0.13.Final.jar",
        "net/java/dev/jna/jna/4.2.2/jna-4.2.2.jar",
        "org/javassist/javassist/3.21.0-GA/javassist-3.21.0-GA.jar",
        "org/jboss/logging/jboss-logging/3.3.2.Final/jboss-logging-3.3.2.Final.jar",
        "org/jboss/threads/jboss-threads/2.3.0.Beta2/jboss-threads-2.3.0.Beta2.jar",
        "org/jboss/xnio/xnio-api/3.6.5.Final/xnio-api-3.6.5.Final.jar",
        "org/jboss/xnio/xnio-nio/3.6.5.Final/xnio-nio-3.6.5.Final.jar",
        "org/jline/jline-reader/3.6.2/jline-reader-3.6.2.jar",
        "org/jline/jline-terminal-jna/3.6.2/jline-terminal-jna-3.6.2.jar",
        "org/jline/jline-terminal/3.6.2/jline-terminal-3.6.2.jar",
        "org/json4s/json4s-ast_2.12/3.5.1/json4s-ast_2.12-3.5.1.jar",
        "org/json4s/json4s-core_2.12/3.5.1/json4s-core_2.12-3.5.1.jar",
        "org/json4s/json4s-jackson_2.12/3.5.1/json4s-jackson_2.12-3.5.1.jar",
        "org/json4s/json4s-scalap_2.12/3.5.1/json4s-scalap_2.12-3.5.1.jar",
        "org/scala-lang/modules/scala-xml_2.12/1.1.0/scala-xml_2.12-1.1.0.jar",
        "org/scala-lang/scala-compiler/2.12.8/scala-compiler-2.12.8.jar",
        "org/scala-lang/scala-library/2.12.8/scala-library-2.12.8.jar",
        "org/scala-lang/scala-reflect/2.12.8/scala-reflect-2.12.8.jar",
        "org/scala-lang/scalap/2.12.6/scalap-2.12.6.jar",
        "org/scala-sbt/test-interface/1.0/test-interface-1.0.jar",
        "org/scalaj/scalaj-http_2.12/2.4.0/scalaj-http_2.12-2.4.0.jar",
        "org/scalameta/cli_2.12/4.0.0/cli_2.12-4.0.0.jar",
        "org/scalameta/common_2.12/4.1.0/common_2.12-4.1.0.jar",
        "org/scalameta/dialects_2.12/4.1.0/dialects_2.12-4.1.0.jar",
        "org/scalameta/fastparse-utils_2.12/1.0.0/fastparse-utils_2.12-1.0.0.jar",
        "org/scalameta/fastparse_2.12/1.0.0/fastparse_2.12-1.0.0.jar",
        "org/scalameta/inputs_2.12/4.1.0/inputs_2.12-4.1.0.jar",
        "org/scalameta/interactive_2.12.7/4.0.0/interactive_2.12.7-4.0.0.jar",
        "org/scalameta/io_2.12/4.1.0/io_2.12-4.1.0.jar",
        "org/scalameta/metabrowse-cli_2.12/0.2.1/metabrowse-cli_2.12-0.2.1.jar",
        "org/scalameta/metabrowse-core_2.12/0.2.1/metabrowse-core_2.12-0.2.1.jar",
        "org/scalameta/metabrowse-server_2.12/0.2.1/metabrowse-server_2.12-0.2.1.jar",
        "org/scalameta/metacp_2.12/4.0.0/metacp_2.12-4.0.0.jar",
        "org/scalameta/mtags_2.12/0.2.0/mtags_2.12-0.2.0.jar",
        "org/scalameta/parsers_2.12/4.1.0/parsers_2.12-4.1.0.jar",
        "org/scalameta/quasiquotes_2.12/4.1.0/quasiquotes_2.12-4.1.0.jar",
        "org/scalameta/scalameta_2.12/4.1.0/scalameta_2.12-4.1.0.jar",
        "org/scalameta/semanticdb-scalac-core_2.12.7/4.0.0/semanticdb-scalac-core_2.12.7-4.0.0.jar",
        "org/scalameta/semanticdb_2.12/4.1.0/semanticdb_2.12-4.1.0.jar",
        "org/scalameta/tokenizers_2.12/4.1.0/tokenizers_2.12-4.1.0.jar",
        "org/scalameta/tokens_2.12/4.1.0/tokens_2.12-4.1.0.jar",
        "org/scalameta/transversers_2.12/4.1.0/transversers_2.12-4.1.0.jar",
        "org/scalameta/trees_2.12/4.1.0/trees_2.12-4.1.0.jar",
        "org/slf4j/slf4j-api/1.8.0-beta2/slf4j-api-1.8.0-beta2.jar",
        "org/slf4j/slf4j-nop/1.7.25/slf4j-nop-1.7.25.jar",
        "org/typelevel/cats-core_2.12/1.1.0/cats-core_2.12-1.1.0.jar",
        "org/typelevel/cats-effect_2.12/0.10/cats-effect_2.12-0.10.jar",
        "org/typelevel/cats-kernel_2.12/1.1.0/cats-kernel_2.12-1.1.0.jar",
        "org/typelevel/cats-macros_2.12/1.1.0/cats-macros_2.12-1.1.0.jar",
        "org/typelevel/machinist_2.12/0.6.2/machinist_2.12-0.6.2.jar",
        "org/typelevel/macro-compat_2.12/1.1.1/macro-compat_2.12-1.1.1.jar",
        "org/wildfly/client/wildfly-client-config/1.0.0.Final/wildfly-client-config-1.0.0.Final.jar",
        "org/wildfly/common/wildfly-common/1.3.0.Final/wildfly-common-1.3.0.Final.jar",
        "org/zeromq/jeromq/0.4.3/jeromq-0.4.3.jar",
        "org/zeromq/jnacl/0.1.0/jnacl-0.1.0.jar",
        "sh/almond/channels_2.12/0.2.2/channels_2.12-0.2.2.jar",
        "sh/almond/interpreter-api_2.12/0.2.2/interpreter-api_2.12-0.2.2.jar",
        "sh/almond/interpreter_2.12/0.2.2/interpreter_2.12-0.2.2.jar",
        "sh/almond/kernel_2.12/0.2.2/kernel_2.12-0.2.2.jar",
        "sh/almond/logger_2.12/0.2.2/logger_2.12-0.2.2.jar",
        "sh/almond/protocol_2.12/0.2.2/protocol_2.12-0.2.2.jar",
        "sh/almond/scala-interpreter_2.12.8/0.2.2/scala-interpreter_2.12.8-0.2.2.jar",
        "sh/almond/scala-kernel-api_2.12.8/0.2.2/scala-kernel-api_2.12.8-0.2.2.jar",
        "sh/almond/scala-kernel_2.12.8/0.2.2/scala-kernel_2.12.8-0.2.2.jar"
      ).map("https://repo1.maven.org/maven2/" + _)

      val urls = l.map(_._1.url).sorted
      assert(urls == expectedUrls)
    }

    test("not delete file in local Maven repo") {
      withTempDir("tmp_dir") { tmpDir =>

        val pomPath     = new File(tmpDir, "org/name/0.1/name-0.1.pom")
        val pomSha1Path = new File(pomPath.getParentFile, pomPath.getName + ".sha1")
        val pomContent =
          """<?xml version='1.0' encoding='UTF-8'?>
            |<project xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://maven.apache.org/POM/4.0.0">
            |    <modelVersion>4.0.0</modelVersion>
            |    <groupId>org</groupId>
            |    <artifactId>name</artifactId>
            |    <version>0.1</version>
            |    <organization>
            |        <name>org</name>
            |        <url>https://github.com/org/name</url>
            |    </organization>
            |    <dependencies>
            |      <dependency>
            |        <groupId>org.scala-lang</groupId>
            |        <artifactId>scala-library</artifactId>
            |        <version>2.13.0</version>
            |      </dependency>
            |    </dependencies>
            |</project>
            |""".stripMargin

        Files.createDirectories(pomPath.getParentFile.toPath)
        Files.write(pomPath.toPath, pomContent.getBytes(StandardCharsets.UTF_8))
        // wrong sha-1
        Files.write(
          pomSha1Path.toPath,
          "da39a3ee5e6b4b0d3255bfef95601890afd80709".getBytes(StandardCharsets.UTF_8)
        )

        assert(pomPath.exists())
        assert(pomSha1Path.exists())

        val repositoryOptions = RepositoryOptions(
          repository = List(tmpDir.toURI.toASCIIString)
        )
        val resolveOptions = SharedResolveOptions(repositoryOptions = repositoryOptions)
        val options        = FetchOptions(resolveOptions = resolveOptions)
        val params         = paramsOrThrow(options)
        val a = Fetch.task(params, pool, Seq("org:name:0.1"))
          .attempt
          .unsafeRun(wrapExceptions = true)(ec)

        assert(a.isLeft)

        a.left.toOption.map(_.getCause).foreach {
          case _: coursier.error.ResolutionError.CantDownloadModule =>
          // expected
          case _ =>
            throw new Exception(s"Unexpected exception type", a.left.toOption.get)
        }

        assert(pomPath.exists())
        assert(pomSha1Path.exists())
      }
    }

    test("Scala version range should work with fully cross-versioned dependencies") {
      val resolutionOpt = ResolutionOptions(
        scalaVersion = Some("2.12.+")
      )
      val resolveOpt = SharedResolveOptions(
        resolutionOptions = resolutionOpt
      )
      val options = FetchOptions(resolveOptions = resolveOpt)
      val params  = paramsOrThrow(options)
      val (_, _, _, files) = Fetch.task(params, pool, Seq("com.lihaoyi:::ammonite:1.8.1"))
        .unsafeRun(wrapExceptions = true)(ec)
      val expectedFiles = Set(
        "ammonite-interp-api_2.12.10-1.8.1.jar",
        "ammonite-interp_2.12.10-1.8.1.jar",
        "ammonite-ops_2.12-1.8.1.jar",
        "ammonite-repl-api_2.12.10-1.8.1.jar",
        "ammonite-repl_2.12.10-1.8.1.jar",
        "ammonite-runtime_2.12.10-1.8.1.jar",
        "ammonite-terminal_2.12-1.8.1.jar",
        "ammonite-util_2.12-1.8.1.jar",
        "ammonite_2.12.10-1.8.1.jar",
        "fansi_2.12-0.2.7.jar",
        "fastparse_2.12-2.1.3.jar",
        "geny_2.12-0.1.8.jar",
        "interface-0.0.8.jar",
        "javaparser-core-3.2.5.jar",
        "javassist-3.21.0-GA.jar",
        "jline-reader-3.6.2.jar",
        "jline-terminal-3.6.2.jar",
        "jline-terminal-jna-3.6.2.jar",
        "jna-4.2.2.jar",
        "os-lib_2.12-0.4.2.jar",
        "pprint_2.12-0.5.6.jar",
        "requests_2.12-0.2.0.jar",
        "scala-collection-compat_2.12-2.1.2.jar",
        "scala-compiler-2.12.10.jar",
        "scala-library-2.12.10.jar",
        "scala-reflect-2.12.10.jar",
        "scala-xml_2.12-1.2.0.jar",
        "scalaparse_2.12-2.1.3.jar",
        "scopt_2.12-3.7.1.jar",
        "sourcecode_2.12-0.1.8.jar",
        "ujson_2.12-0.8.0.jar",
        "upack_2.12-0.8.0.jar",
        "upickle-core_2.12-0.8.0.jar",
        "upickle-implicits_2.12-0.8.0.jar",
        "upickle_2.12-0.8.0.jar"
      )
      assert(files.map(_._2.getName).toSet.equals(expectedFiles))
    }

    // sbt-plugin-example-diamond is a diamond graph of sbt plugins.
    // Diamond depends on left and right which both depend on bottom.
    //             sbt-plugin-example-diamond
    //                        / \
    // sbt-plugin-example-left   sbt-plugin-example-right
    //                        \ /
    //             sbt-plugin-example-bottom
    // Depending on the version of sbt-plugin-example-diamond, different patterns
    // are tested:
    // - Some plugins are only published to the deprecated Maven path, some to the new
    // - There may be some conflict resolution to perform on sbt-plugin-example-bottom,
    //   mixing old and new Maven paths.
    test("sbt-plugin-example-diamond") {

      def checkResolveDiamond(version: String)(expectedJars: String*): Unit = {
        val options = FetchOptions(
          resolveOptions = SharedResolveOptions(
            dependencyOptions = DependencyOptions(
              sbtPlugin = List(s"ch.epfl.scala:sbt-plugin-example-diamond:$version")
            )
          )
        )
        val params = paramsOrThrow(options)
        val (_, _, _, files) =
          Fetch.task(params, pool, Seq.empty).unsafeRun(wrapExceptions = true)(ec)

        val obtained = files.map(_._2.getName).toSet
        assert(obtained == expectedJars.toSet)
      }

      // only deprecated Maven paths
      test("0.1.0") {
        checkResolveDiamond("0.1.0")(
          "sbt-plugin-example-diamond-0.1.0.jar",
          "sbt-plugin-example-left-0.1.0.jar",
          "sbt-plugin-example-right-0.1.0.jar",
          "sbt-plugin-example-bottom-0.1.0.jar"
        )
      }

      // diamond and left use the new Maven path
      test("0.2.0") {
        checkResolveDiamond("0.2.0")(
          "sbt-plugin-example-diamond_2.12_1.0-0.2.0.jar",
          "sbt-plugin-example-left_2.12_1.0-0.2.0.jar",
          "sbt-plugin-example-right-0.1.0.jar",
          "sbt-plugin-example-bottom-0.1.0.jar"
        )
      }

      // conflict resolution between new and deprecated Maven paths
      test("0.3.0") {
        checkResolveDiamond("0.3.0")(
          "sbt-plugin-example-diamond_2.12_1.0-0.3.0.jar",
          "sbt-plugin-example-left_2.12_1.0-0.3.0.jar",
          "sbt-plugin-example-right-0.1.0.jar",
          "sbt-plugin-example-bottom_2.12_1.0-0.2.0.jar"
        )
      }

      // bottom use the new Maven path but not right
      test("0.4.0") {
        checkResolveDiamond("0.4.0")(
          "sbt-plugin-example-diamond_2.12_1.0-0.4.0.jar",
          "sbt-plugin-example-left_2.12_1.0-0.3.0.jar",
          "sbt-plugin-example-right-0.2.0.jar",
          "sbt-plugin-example-bottom_2.12_1.0-0.2.0.jar"
        )
      }

      // only new Maven paths with conflict resolution on bottom
      test("0.5.0") {
        checkResolveDiamond("0.5.0")(
          "sbt-plugin-example-diamond_2.12_1.0-0.5.0.jar",
          "sbt-plugin-example-left_2.12_1.0-0.3.0.jar",
          "sbt-plugin-example-right_2.12_1.0-0.3.0.jar",
          "sbt-plugin-example-bottom_2.12_1.0-0.3.0.jar"
        )
      }
    }
  }
}
