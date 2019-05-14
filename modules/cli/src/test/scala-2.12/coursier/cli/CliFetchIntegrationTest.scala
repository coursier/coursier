package coursier.cli

import java.io._
import java.net.URLEncoder.encode

import argonaut.Argonaut._
import caseapp.core.RemainingArgs
import coursier.cli.options._
import coursier.cli.options.shared._
import coursier.cli.util.{DepNode, ReportNode}
import java.io._
import java.net.URLClassLoader
import java.net.URLEncoder.encode
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Paths}

import cats.data.Validated
import coursier.cache.CacheDefaults
import coursier.cli.fetch.Fetch
import coursier.cli.launch.Launch
import coursier.cli.params.FetchParams
import coursier.cli.resolve.ResolveException
import coursier.util.Sync
import org.junit.runner.RunWith
import org.scalatest.{FlatSpec, Matchers}
import org.scalatestplus.junit.JUnitRunner

import scala.concurrent.ExecutionContext
import scala.io.Source

@RunWith(classOf[JUnitRunner])
class CliFetchIntegrationTest extends FlatSpec with CliTestLib with Matchers {

  val pool = Sync.fixedThreadPool(CacheDefaults.concurrentDownloadCount)
  val ec = ExecutionContext.fromExecutorService(pool)

  def paramsOrThrow(options: FetchOptions): FetchParams =
    FetchParams(options) match {
      case Validated.Invalid(errors) =>
        sys.error("Got errors:\n" + errors.toList.map(e => s"  $e\n").mkString)
      case Validated.Valid(params0) =>
        params0
    }

  def getReportFromJson(f: File): ReportNode = {
    // Parse back the output json file
    val source = scala.io.Source.fromFile(f)
    val str = try source.mkString finally source.close()

    str.decodeEither[ReportNode] match {
      case Left(error) =>
        throw new Exception(s"Error while decoding report: $error")
      case Right(report) => report
    }
  }

  private val fileNameLength: DepNode => Int = _.file.getOrElse("").length

  "Normal fetch" should "get all files" in {
    val options = FetchOptions()
    val params = paramsOrThrow(options)
    val (_, files) = Fetch.task(params, pool, Seq("junit:junit:4.12"))
      .unsafeRun()(ec)
    assert(files.map(_._2.getName).toSet.equals(Set("junit-4.12.jar", "hamcrest-core-1.3.jar")))
  }

  "Underscore classifier" should "fetch default files" in {
    val artifactOpt = ArtifactOptions(
      classifier = List("_")
    )
    val options = FetchOptions(artifactOptions = artifactOpt)
    val params = paramsOrThrow(options)
    val (_, files) = Fetch.task(params, pool, Seq("junit:junit:4.12"))
      .unsafeRun()(ec)
    assert(files.map(_._2.getName).toSet.equals(Set("junit-4.12.jar", "hamcrest-core-1.3.jar")))
  }

  "Underscore and source classifier" should "fetch default and source files" in {
    val artifactOpt = ArtifactOptions(
      classifier = List("_"),
      sources = true
    )
    val options = FetchOptions(artifactOptions = artifactOpt)
    val params = paramsOrThrow(options)
    val (_, files) = Fetch.task(params, pool, Seq("junit:junit:4.12"))
      .unsafeRun()(ec)
    println(files)
    assert(files.map(_._2.getName).toSet.equals(Set(
      "junit-4.12.jar",
      "junit-4.12-sources.jar",
      "hamcrest-core-1.3.jar",
      "hamcrest-core-1.3-sources.jar"
    )))
  }

  "Default and source options" should "fetch default and source files" in {
    val artifactOpt = ArtifactOptions(
      default = Some(true),
      sources = true
    )
    val options = FetchOptions(
      artifactOptions = artifactOpt
    )
    val params = paramsOrThrow(options)
    val (_, files) = Fetch.task(params, pool, Seq("junit:junit:4.12"))
      .unsafeRun()(ec)
    assert(files.map(_._2.getName).toSet.equals(Set(
      "junit-4.12.jar",
      "junit-4.12-sources.jar",
      "hamcrest-core-1.3.jar",
      "hamcrest-core-1.3-sources.jar"
    )))
  }

  "scalafmt-cli fetch" should "discover all main classes" in {
    val options = FetchOptions()
    val params = paramsOrThrow(options)
    val (_, files) = Fetch.task(params, pool, Seq("com.geirsson:scalafmt-cli_2.12:1.4.0"))
      .unsafeRun()(ec)
    val loader = new URLClassLoader(files.map(_._2.toURI.toURL).toArray, Launch.baseLoader)
    assert(Launch.mainClasses(loader) == Map(
      ("", "") -> "com.martiansoftware.nailgun.NGServer",
      ("com.geirsson", "cli") -> "org.scalafmt.cli.Cli"
    ))
  }

  "scalafix-cli fetch" should "discover all main classes" in {
    val options = FetchOptions()
    val params = paramsOrThrow(options)
    val (_, files) = Fetch.task(params, pool, Seq("ch.epfl.scala:scalafix-cli_2.12.4:0.5.10"))
      .unsafeRun()(ec)
    val loader = new URLClassLoader(files.map(_._2.toURI.toURL).toArray, Launch.baseLoader)
    assert(Launch.mainClasses(loader) == Map(
      ("", "") -> "com.martiansoftware.nailgun.NGServer",
      ("ch.epfl.scala", "cli") -> "scalafix.cli.Cli"
    ))
  }

  "ammonite fetch" should "discover all main classes" in {
    val options = FetchOptions()
    val params = paramsOrThrow(options)
    val (_, files) = Fetch.task(params, pool, Seq("com.lihaoyi:ammonite_2.12.4:1.1.0"))
      .unsafeRun()(ec)
    val loader = new URLClassLoader(files.map(_._2.toURI.toURL).toArray, Launch.baseLoader)
    assert(Launch.mainClasses(loader) == Map(
      ("", "Javassist") -> "javassist.CtClass",
      ("" ,"Java Native Access (JNA)") -> "com.sun.jna.Native",
      ("com.lihaoyi", "ammonite") -> "ammonite.Main"
    ))
  }

  "sssio fetch" should "discover all main classes" in {
    val options = FetchOptions()
    val params = paramsOrThrow(options)
    val (_, files) = Fetch.task(params, pool, Seq("lt.dvim.sssio:sssio_2.12:0.0.1"))
      .unsafeRun()(ec)
    val loader = new URLClassLoader(files.map(_._2.toURI.toURL).toArray, Launch.baseLoader)
    assert(Launch.mainClasses(loader) == Map(
      ("", "") -> "com.kenai.jffi.Main",
      ("lt.dvim.sssio", "sssio") -> "lt.dvim.sssio.Sssio"
    ))
  }

  "Module level" should "exclude correctly" in withFile(
    "junit:junit--org.hamcrest:hamcrest-core") { (file, _) =>
    withFile() { (jsonFile, _) =>
      val dependencyOpt = DependencyOptions(localExcludeFile = file.getAbsolutePath)
      val resolveOpt = ResolveOptions(dependencyOptions = dependencyOpt)
      val options = FetchOptions(jsonOutputFile = jsonFile.getPath, resolveOptions = resolveOpt)
      val params = paramsOrThrow(options)

      val (_, files) = Fetch.task(params, pool, Seq("junit:junit:4.12"))
        .unsafeRun()(ec)
      val filesFetched = files.map(_._2.getName).toSet
      val expected = Set("junit-4.12.jar")
      assert(filesFetched.equals(expected), s"files fetched: $filesFetched not matching expected: $expected")

      val node: ReportNode = getReportFromJson(jsonFile)

      assert(node.dependencies.length == 1)
      assert(node.dependencies.head.coord == "junit:junit:4.12")
    }

  }

  /**
    * Result without exclusion:
    * |└─ org.apache.avro:avro:1.7.4
    * |├─ com.thoughtworks.paranamer:paranamer:2.3
    * |├─ org.apache.commons:commons-compress:1.4.1
    * |│  └─ org.tukaani:xz:1.0 // this should be fetched
    * |├─ org.codehaus.jackson:jackson-core-asl:1.8.8
    * |├─ org.codehaus.jackson:jackson-mapper-asl:1.8.8
    * |│  └─ org.codehaus.jackson:jackson-core-asl:1.8.8
    * |├─ org.slf4j:slf4j-api:1.6.4
    * |└─ org.xerial.snappy:snappy-java:1.0.4.1
    */
  "avro exclude xz" should "not fetch xz" in withFile(
    "org.apache.avro:avro--org.tukaani:xz") { (file, writer) =>
    withFile() { (jsonFile, _) =>
      val dependencyOpt = DependencyOptions(localExcludeFile = file.getAbsolutePath)
      val resolveOpt = ResolveOptions(dependencyOptions = dependencyOpt)
      val options = FetchOptions(jsonOutputFile = jsonFile.getPath, resolveOptions = resolveOpt)
      val params = paramsOrThrow(options)

      val (_, files) = Fetch.task(params, pool, Seq("org.apache.avro:avro:1.7.4"))
        .unsafeRun()(ec)

      val filesFetched = files.map(_._2.getName).toSet
      assert(!filesFetched.contains("xz-1.0.jar"))

      val node: ReportNode = getReportFromJson(jsonFile)

      // assert root level dependencies
      assert(node.dependencies.map(_.coord).toSet == Set(
        "org.apache.avro:avro:1.7.4",
        "com.thoughtworks.paranamer:paranamer:2.3",
        "org.apache.commons:commons-compress:1.4.1",
        "org.codehaus.jackson:jackson-core-asl:1.8.8",
        "org.codehaus.jackson:jackson-mapper-asl:1.8.8",
        "org.slf4j:slf4j-api:1.6.4",
        "org.xerial.snappy:snappy-java:1.0.4.1"
      ))

      // org.apache.commons:commons-compress:1.4.1 should not contain deps underneath it.
      val compressNode = node.dependencies.find(_.coord == "org.apache.commons:commons-compress:1.4.1")
      assert(node.dependencies.exists(_.coord == "org.apache.commons:commons-compress:1.4.1"))
      assert(compressNode.get.dependencies.isEmpty)
    }
  }

  /**
    * Result without exclusion:
    * |├─ org.apache.avro:avro:1.7.4
    * |│  ├─ com.thoughtworks.paranamer:paranamer:2.3
    * |│  ├─ org.apache.commons:commons-compress:1.4.1
    * |│  │  └─ org.tukaani:xz:1.0
    * |│  ├─ org.codehaus.jackson:jackson-core-asl:1.8.8
    * |│  ├─ org.codehaus.jackson:jackson-mapper-asl:1.8.8
    * |│  │  └─ org.codehaus.jackson:jackson-core-asl:1.8.8
    * |│  ├─ org.slf4j:slf4j-api:1.6.4
    * |│  └─ org.xerial.snappy:snappy-java:1.0.4.1
    * |└─ org.apache.commons:commons-compress:1.4.1
    * |   └─ org.tukaani:xz:1.0
    */
  "avro excluding xz + commons-compress" should "still fetch xz" in withFile(
    "org.apache.avro:avro--org.tukaani:xz") {
    (file, writer) =>

      withFile() {
        (jsonFile, _) => {
          val dependencyOpt = DependencyOptions(localExcludeFile = file.getAbsolutePath)
          val resolveOpt = ResolveOptions(dependencyOptions = dependencyOpt)
          val options = FetchOptions(jsonOutputFile = jsonFile.getPath, resolveOptions = resolveOpt)
          val params = paramsOrThrow(options)

          val (_, files) = Fetch.task(params, pool, Seq("org.apache.avro:avro:1.7.4", "org.apache.commons:commons-compress:1.4.1"))
            .unsafeRun()(ec)
          val filesFetched = files.map(_._2.getName).toSet
          assert(filesFetched.contains("xz-1.0.jar"))

          val node: ReportNode = getReportFromJson(jsonFile)

          // Root level org.apache.commons:commons-compress:1.4.1 should have org.tukaani:xz:1.0 underneath it.
          val compressNode = node.dependencies.find(_.coord == "org.apache.commons:commons-compress:1.4.1")
          assert(compressNode.isDefined)
          assert(compressNode.get.dependencies.contains("org.tukaani:xz:1.0"))

          val innerCompressNode = node.dependencies.find(_.coord == "org.apache.avro:avro:1.7.4")
          assert(innerCompressNode.isDefined)
          assert(!innerCompressNode.get.dependencies.contains("org.tukaani:xz:1.0"))
        }
      }

  }

  /**
    * Result:
    * |├─ org.apache.commons:commons-compress:1.4.1
    * |│  └─ org.tukaani:xz:1.0 -> 1.1
    * |└─ org.tukaani:xz:1.1
    */
  "requested xz:1.1" should "not have conflicts" in withFile() {
    (excludeFile, writer) =>
      withFile() {
        (jsonFile, _) => {
          val options = FetchOptions(jsonOutputFile = jsonFile.getPath)
          val params = paramsOrThrow(options)

          Fetch.task(params, pool, Seq("org.apache.commons:commons-compress:1.4.1", "org.tukaani:xz:1.1"))
            .unsafeRun()(ec)

          val node: ReportNode = getReportFromJson(jsonFile)
          assert(node.conflict_resolution.isEmpty)
        }
      }
  }

  /**
    * Result:
    * |├─ org.apache.commons:commons-compress:1.5
    * |│  └─ org.tukaani:xz:1.2
    * |└─ org.tukaani:xz:1.1 -> 1.2
    */
  "org.apache.commons:commons-compress:1.5 org.tukaani:xz:1.1" should "have conflicts" in withFile() {
    (excludeFile, _) =>
      withFile() {
        (jsonFile, _) => {
          val options = FetchOptions(jsonOutputFile = jsonFile.getPath)
          val params = paramsOrThrow(options)

          Fetch.task(params, pool, Seq("org.apache.commons:commons-compress:1.5", "org.tukaani:xz:1.1"))
            .unsafeRun()(ec)

          val node: ReportNode = getReportFromJson(jsonFile)
          assert(node.conflict_resolution == Map("org.tukaani:xz:1.1" -> "org.tukaani:xz:1.2"))
        }
      }
  }

  /**
    * Result:
    * |└─ org.apache.commons:commons-compress:1.5
    * |   └─ org.tukaani:xz:1.2
    */
  "classifier tests" should "have tests.jar" in withFile() {
    (excludeFile, _) =>
      withFile() {
        (jsonFile, _) => {
          val options = FetchOptions(jsonOutputFile = jsonFile.getPath)
          val params = paramsOrThrow(options)

          Fetch.task(
            params,
            pool,
            Seq("org.apache.commons:commons-compress:1.5,classifier=tests")
          ).unsafeRun()(ec)

          val node: ReportNode = getReportFromJson(jsonFile)

          val compressNode = node.dependencies.find(_.coord == "org.apache.commons:commons-compress:jar:tests:1.5")

          assert(compressNode.isDefined)
          compressNode.get.file.map(f => assert(f.contains("commons-compress-1.5-tests.jar"))).orElse(fail("Not Defined"))
          assert(compressNode.get.dependencies.contains("org.tukaani:xz:1.2"))
        }
      }
  }

  /**
    * Result:
    * |├─ org.apache.commons:commons-compress:1.5
    * |│  └─ org.tukaani:xz:1.2
    * |└─ org.apache.commons:commons-compress:1.5
    * |   └─ org.tukaani:xz:1.2
    */
  "mixed vanilla and classifier " should "have tests.jar and .jar" in withFile() {
    (excludeFile, _) =>
      withFile() {
        (jsonFile, _) => {
          val options = FetchOptions(jsonOutputFile = jsonFile.getPath)
          val params = paramsOrThrow(options)

          Fetch.task(
            params,
            pool,
            Seq(
              "org.apache.commons:commons-compress:1.5,classifier=tests",
              "org.apache.commons:commons-compress:1.5"
            )
          ).unsafeRun()(ec)

          val node: ReportNode = getReportFromJson(jsonFile)

          val compressNodes: Seq[DepNode] = node.dependencies
            .filter(_.coord.startsWith("org.apache.commons:commons-compress"))
            .sortBy(_.coord.length) // sort by coord length

          assert(compressNodes.length == 2)
          assert(compressNodes.head.coord == "org.apache.commons:commons-compress:1.5")
          compressNodes.head.file.map( f => assert(f.contains("commons-compress-1.5.jar"))).orElse(fail("Not Defined"))

          assert(compressNodes.last.coord == "org.apache.commons:commons-compress:jar:tests:1.5")
          compressNodes.last.file.map( f => assert(f.contains("commons-compress-1.5-tests.jar"))).orElse(fail("Not Defined"))
        }
      }
  }

  /**
    * Result:
    * |└─ org.apache.commons:commons-compress:1.5
    * |   └─ org.tukaani:xz:1.2 // should not be fetched
    */
  "intransitive" should "only fetch a single jar" in withFile() {
    (_, _) =>
      withFile() {
        (jsonFile, _) => {
          val dependencyOpt = DependencyOptions(intransitive = List("org.apache.commons:commons-compress:1.5"))
          val resolveOpt = ResolveOptions(dependencyOptions = dependencyOpt)
          val options = FetchOptions(jsonOutputFile = jsonFile.getPath, resolveOptions = resolveOpt)
          val params = paramsOrThrow(options)

          Fetch.task(params, pool, Nil)
            .unsafeRun()(ec)

          val node: ReportNode = getReportFromJson(jsonFile)
          val compressNode = node.dependencies.find(_.coord == "org.apache.commons:commons-compress:1.5")
          assert(compressNode.isDefined)
          compressNode.get.file.map( f => assert(f.contains("commons-compress-1.5.jar"))).orElse(fail("Not Defined"))

          assert(compressNode.get.dependencies.isEmpty)
        }
      }
  }

  /**
    * Result:
    * |└─ org.apache.commons:commons-compress:1.5
    * |   └─ org.tukaani:xz:1.2
    */
  "intransitive classifier" should "only fetch a single tests jar" in withFile() {
    (excludeFile, _) =>
      withFile() {
        (jsonFile, _) => {
          val dependencyOpt = DependencyOptions(intransitive = List("org.apache.commons:commons-compress:1.5,classifier=tests"))
          val resolveOpt = ResolveOptions(dependencyOptions = dependencyOpt)
          val options = FetchOptions(jsonOutputFile = jsonFile.getPath, resolveOptions = resolveOpt)
          val params = paramsOrThrow(options)

          Fetch.task(params, pool, Seq())
            .unsafeRun()(ec)

          val node: ReportNode = getReportFromJson(jsonFile)

          val compressNode = node.dependencies.find(_.coord == "org.apache.commons:commons-compress:jar:tests:1.5")
          assert(compressNode.isDefined)
          compressNode.get.file.map( f => assert(f.contains("commons-compress-1.5-tests.jar"))).orElse(fail("Not Defined"))

          assert(compressNode.get.dependencies.isEmpty)
        }
      }
  }

  /**
    * Result:
    * |└─ org.apache.commons:commons-compress:1.5 -> 1.4.1
    * |   └─ org.tukaani:xz:1.0
    */
  "classifier with forced version" should "fetch tests jar" in withFile() {
    (excludeFile, _) =>
      withFile() {
        (jsonFile, _) => {
          val resolutionOpt = ResolutionOptions(forceVersion = List("org.apache.commons:commons-compress:1.4.1"))
          val resolveOpt = ResolveOptions(resolutionOptions = resolutionOpt)
          val options = FetchOptions(jsonOutputFile = jsonFile.getPath, resolveOptions = resolveOpt)
          val params = paramsOrThrow(options)

          Fetch.task(
            params,
            pool,
            Seq("org.apache.commons:commons-compress:1.5,classifier=tests")
          ).unsafeRun()(ec)

          val node: ReportNode = getReportFromJson(jsonFile)

          assert(!node.dependencies.exists(_.coord == "org.apache.commons:commons-compress:1.5"))

          val compressNode = node.dependencies.find(_.coord == "org.apache.commons:commons-compress:jar:tests:1.4.1")

          assert(compressNode.isDefined)
          compressNode.get.file.map( f => assert(f.contains("commons-compress-1.4.1-tests.jar"))).orElse(fail("Not Defined"))

          assert(compressNode.get.dependencies.size == 1)
          assert(compressNode.get.dependencies.head == "org.tukaani:xz:1.0")
        }
      }
  }

  /**
    * Result:
    * |└─ org.apache.commons:commons-compress:1.5 -> 1.4.1
    * |   └─ org.tukaani:xz:1.0 // should not be there
    */
  "intransitive, classifier, forced version" should "fetch a single tests jar" in withFile() {
    (excludeFile, _) =>
      withFile() {
        (jsonFile, _) => {
          val resolutionOpt = ResolutionOptions(
            forceVersion = List("org.apache.commons:commons-compress:1.4.1")
          )
          val dependencyOpt = DependencyOptions(
            intransitive = List("org.apache.commons:commons-compress:1.5,classifier=tests")
          )
          val resolveOpt = ResolveOptions(
            resolutionOptions = resolutionOpt,
            dependencyOptions = dependencyOpt
          )
          val options = FetchOptions(jsonOutputFile = jsonFile.getPath, resolveOptions = resolveOpt)
          val params = paramsOrThrow(options)

          Fetch.task(params, pool, Seq())
            .unsafeRun()(ec)

          val node: ReportNode = getReportFromJson(jsonFile)

          assert(!node.dependencies.exists(_.coord == "org.apache.commons:commons-compress:1.5"))

          val compressNode = node.dependencies.find(_.coord == "org.apache.commons:commons-compress:jar:tests:1.4.1")

          assert(compressNode.isDefined)
          compressNode.get.file.map( f => assert(f.contains("commons-compress-1.4.1-tests.jar"))).orElse(fail("Not Defined"))

          assert(compressNode.get.dependencies.isEmpty)
        }
      }
  }

  "profiles" should "be manually (de)activated" in withFile() {
    (jsonFile, _) =>
      val resolutionOpt = ResolutionOptions(profile = List("scala-2.10", "!scala-2.11"))
      val resolveOpt = ResolveOptions(
        resolutionOptions = resolutionOpt
      )
      val options = FetchOptions(jsonOutputFile = jsonFile.getPath, resolveOptions = resolveOpt)
      val params = paramsOrThrow(options)

      Fetch.task(
        params,
        pool,
        Seq("org.apache.spark:spark-core_2.10:2.2.1")
      ).unsafeRun()(ec)

      val node = getReportFromJson(jsonFile)

      assert(node.dependencies.exists(_.coord.startsWith("org.scala-lang:scala-library:2.10.")))
      assert(!node.dependencies.exists(_.coord.startsWith("org.scala-lang:scala-library:2.11.")))
  }

  "com.spotify:helios-testing:0.9.193" should "have dependencies with classifiers" in withFile() {
    (excludeFile, _) =>
      withFile() {
        (jsonFile, _) => {
          val options = FetchOptions(jsonOutputFile = jsonFile.getPath)
          val params = paramsOrThrow(options)

          val heliosCoord = "com.spotify:helios-testing:0.9.193"

          Fetch.task(
            params,
            pool,
            Seq(heliosCoord)
          ).unsafeRun()(ec)
          val node: ReportNode = getReportFromJson(jsonFile)
          val testEntry: DepNode = node.dependencies.find(_.coord == heliosCoord).get
          assert(
            testEntry.dependencies.exists(_.startsWith("com.spotify:docker-client:jar:shaded:")))
          assert(
            node.dependencies.exists(_.coord.startsWith("com.spotify:docker-client:jar:shaded:")))
        }
      }
  }

  /**
   * Result:
   * |└─ a:b:c
   */
  "local file dep url" should "have coursier-fetch-test.jar and cached for second run" in withFile() {
    (jsonFile, _) => {
      withFile("tada", "coursier-fetch-test", ".jar") {
        (testFile, _) => {
          val path = testFile.getAbsolutePath
          val encodedUrl = encode("file://" + path, "UTF-8")


          val cacheOpt = CacheOptions(cacheFileArtifacts = true)
          val resolveOpt = ResolveOptions(cacheOptions = cacheOpt)
          val options = FetchOptions(jsonOutputFile = jsonFile.getPath, resolveOptions = resolveOpt)
          val params = paramsOrThrow(options)

          // fetch with encoded url set to temp jar
          val task = Fetch.task(
            params,
            pool,
            Seq(
              "a:b:c,url=" + encodedUrl
            )
          )
          task.unsafeRun()(ec)

          val node1: ReportNode = getReportFromJson(jsonFile)

          val depNodes1: Seq[DepNode] = node1.dependencies
            .filter(_.coord == "a:b:c")
            .sortBy(fileNameLength)
          assert(depNodes1.length == 1)

          val urlInJsonFile1 = depNodes1.head.file.get
          assert(urlInJsonFile1.contains(path))

          // open jar and inspect contents
          val fileContents1 = Source.fromFile(urlInJsonFile1).getLines.mkString
          assert(fileContents1 == "tada")

          testFile.delete()

          task.unsafeRun()(ec)

          val node2: ReportNode = getReportFromJson(jsonFile)

          val depNodes2: Seq[DepNode] = node2.dependencies
            .filter(_.coord == "a:b:c")
            .sortBy(fileNameLength)
          assert(depNodes2.length == 1)

          val urlInJsonFile2 = depNodes2.head.file.get
          val inCoursierCache =
            urlInJsonFile2.contains("/.coursier/") || // Former cache path
              urlInJsonFile2.contains("/coursier/") || // New cache path, Linux
              urlInJsonFile2.contains("/Coursier/") // New cache path, OS X
          assert(inCoursierCache && urlInJsonFile2.contains(testFile.toString))
        }
      }
    }
  }

  /**
   * Result:
   * |└─ org.apache.commons:commons-compress:1.5
   */
  "external dep url" should "fetch junit-4.12.jar" in withFile() {
    (jsonFile, _) => {
      val options = FetchOptions(jsonOutputFile = jsonFile.getPath)
      val params = paramsOrThrow(options)

      // encode path to different jar than requested
      val externalUrl = encode("http://central.maven.org/maven2/junit/junit/4.12/junit-4.12.jar", "UTF-8")

      // fetch with different artifact url
      Fetch.task(
        params,
        pool,
        Seq(
          "org.apache.commons:commons-compress:1.5,url=" + externalUrl
        )
      ).unsafeRun()(ec)

      val node: ReportNode = getReportFromJson(jsonFile)

      val depNodes: Seq[DepNode] = node.dependencies
        .filter(_.coord == "org.apache.commons:commons-compress:1.5")
        .sortBy(fileNameLength)
      assert(depNodes.length == 1)
      depNodes.head.file.map( f => assert(f.contains("junit/junit/4.12/junit-4.12.jar"))).orElse(fail("Not Defined"))
    }
  }

  /**
   * Result:
   * |└─ h:i:j
   */
  "external dep url with arbitrary coords" should "fetch junit-4.12.jar" in withFile() {
    (jsonFile, _) => {
      val options = FetchOptions(jsonOutputFile = jsonFile.getPath)
      val params = paramsOrThrow(options)

      // encode path to different jar than requested
      val externalUrl = encode("http://central.maven.org/maven2/junit/junit/4.12/junit-4.12.jar", "UTF-8")

      // arbitrary coords fail to fetch because... coords need to exist in a repo somewhere to work. fix this.
      Fetch.task(
        params,
        pool,
        Seq(
          "h:i:j,url=" + externalUrl
        )
      ).unsafeRun()(ec)

      val node: ReportNode = getReportFromJson(jsonFile)

      val depNodes: Seq[DepNode] = node.dependencies
        .filter(_.coord == "h:i:j")
        .sortBy(fileNameLength)
      assert(depNodes.length == 1)
      depNodes.head.file.map( f => assert(f.contains("junit/junit/4.12/junit-4.12.jar"))).orElse(fail("Not Defined"))
    }
  }

  /**
   * Result:
   * |└─ org.apache.commons:commons-compress:1.5
   */
  "external dep url with classifier" should "fetch junit-4.12.jar and classifier gets thrown away" in withFile() {
    (jsonFile, _) => {
      val options = FetchOptions(jsonOutputFile = jsonFile.getPath)
      val params = paramsOrThrow(options)

      // encode path to different jar than requested
      val externalUrl = encode("http://central.maven.org/maven2/junit/junit/4.12/junit-4.12.jar", "UTF-8")

      Fetch.task(
        params,
        pool,
        Seq(
          "org.apache.commons:commons-compress:1.5,url=" + externalUrl + ",classifier=tests"
        )
      ).unsafeRun()(ec)

      val node: ReportNode = getReportFromJson(jsonFile)

      val depNodes: Seq[DepNode] = node.dependencies
        .filter(_.coord.startsWith("org.apache.commons:commons-compress:"))
        .sortBy(fileNameLength)


      val coords: Seq[String] = node.dependencies
        .map(_.coord)
        .sorted

      assert(depNodes.length == 1)
      // classifier doesn't matter when we have a url so it is not listed
      depNodes.head.file.map( f => assert(f.contains("junit/junit/4.12/junit-4.12.jar"))).orElse(fail("Not Defined"))
    }
  }

  /**
   * Result:
   * |└─ org.apache.commons:commons-compress:1.5
   * |   └─ org.tukaani:xz:1.2
   * |└─ org.tukaani:xz:1.2 // with the file from the URL
   */
  "external dep url with classifier that is a transitive dep" should "fetch junit-4.12.jar and classifier gets thrown away" in withFile() {
    (jsonFile, _) => {
      val options = FetchOptions(jsonOutputFile = jsonFile.getPath)
      val params = paramsOrThrow(options)

      // encode path to different jar than requested
      val externalUrl = encode("http://central.maven.org/maven2/junit/junit/4.12/junit-4.12.jar", "UTF-8")

      Fetch.task(
        params,
        pool,
        Seq(
          "org.apache.commons:commons-compress:1.5",
          "org.tukaani:xz:1.2,classifier=tests,url="+externalUrl
        )
      ).unsafeRun()(ec)

      val node: ReportNode = getReportFromJson(jsonFile)
      val depNodes: Seq[DepNode] = node.dependencies
        .filter(_.coord.startsWith("org.tukaani:xz:"))
        .sortBy(fileNameLength)
      val coords: Seq[String] = node.dependencies.map(_.coord).sorted

      assert(coords == Seq("org.apache.commons:commons-compress:1.5", "org.tukaani:xz:1.2"))
      assert(depNodes.length == 1)
      assert(depNodes.last.file.isDefined)
      depNodes.last.file.map( f => assert(f.contains("junit/junit/4.12/junit-4.12.jar"))).orElse(fail("Not Defined"))
    }
  }

  /**
   * Result:
   * |└─ org.apache.commons:commons-compress:1.5,classifier=sources
   *     └─ org.tukaani:xz:1.2,classifier=sources
   */
  "classifier sources" should "fetch sources jar" in withFile() {
    (jsonFile, _) => {
      val artifactOpt = ArtifactOptions(sources = true)
      val options = FetchOptions(jsonOutputFile = jsonFile.getPath, artifactOptions = artifactOpt)
      val params = paramsOrThrow(options)

      // encode path to different jar than requested

      Fetch.task(
        params,
        pool,
        Seq(
          "org.apache.commons:commons-compress:1.5,classifier=sources"
        )
      ).unsafeRun()(ec)
      val node: ReportNode = getReportFromJson(jsonFile)
      val coords: Seq[String] = node.dependencies.map(_.coord).sorted
      val depNodes: Seq[DepNode] = node.dependencies
        .filter(_.coord.startsWith("org.apache.commons"))
        .sortBy(fileNameLength)

      assert(depNodes.length == 1)
      assert(depNodes.head.file.isDefined)
      depNodes.head.file.map(f => assert(f.contains("1.5-sources.jar"))).orElse(fail("Not Defined"))
      depNodes.head.dependencies.foreach(d => {
        assert(d.contains(":sources:"))
      })

      assert(coords == Seq(
        "org.apache.commons:commons-compress:jar:sources:1.5",
        "org.tukaani:xz:jar:sources:1.2")
      )
    }
  }

  /**
   * Result:
   * |└─ org.apache.commons:commons-compress:1.5
   * |└─ org.codehaus.jackson:jackson-mapper-asl:1.8.8
   * |   └─ org.codehaus.jackson:jackson-core-asl:1.8.8
   */
  "external dep url with another dep" should "fetch junit-4.12.jar and jars for jackson-mapper" in withFile() {
    (jsonFile, _) => {
      val options = FetchOptions(jsonOutputFile = jsonFile.getPath)
      val params = paramsOrThrow(options)

      val externalUrl = encode("http://central.maven.org/maven2/junit/junit/4.12/junit-4.12.jar", "UTF-8")

      Fetch.task(
        params,
        pool,
        Seq(
          "org.apache.commons:commons-compress:1.5,url=" + externalUrl,
          "org.codehaus.jackson:jackson-mapper-asl:1.8.8"
        )
      ).unsafeRun()(ec)

      val node: ReportNode = getReportFromJson(jsonFile)

      val depNodes: Seq[DepNode] = node.dependencies
      assert(depNodes.length == 3)

      val compressNodes = depNodes
        .filter(_.coord == "org.apache.commons:commons-compress:1.5")
        .sortBy(fileNameLength)
      assert(compressNodes.length == 1)
      compressNodes.head.file.map( f => assert(f.contains("junit/junit/4.12/junit-4.12.jar"))).orElse(fail("Not Defined"))

      val jacksonMapperNodes = depNodes
        .filter(_.coord == "org.codehaus.jackson:jackson-mapper-asl:1.8.8")
        .sortBy(fileNameLength)
      assert(jacksonMapperNodes.length == 1)
      jacksonMapperNodes.head.file.map( f => assert(f.contains("org/codehaus/jackson/jackson-mapper-asl/1.8.8/jackson-mapper-asl-1.8.8.jar"))).orElse(fail("Not Defined"))
      assert(jacksonMapperNodes.head.dependencies.size == 1)
      assert(jacksonMapperNodes.head.dependencies.head == "org.codehaus.jackson:jackson-core-asl:1.8.8")

      val jacksonCoreNodes = depNodes
        .filter(_.coord == "org.codehaus.jackson:jackson-core-asl:1.8.8")
        .sortBy(fileNameLength)
      assert(jacksonCoreNodes.length == 1)
      jacksonCoreNodes.head.file.map( f => assert(f.contains("org/codehaus/jackson/jackson-core-asl/1.8.8/jackson-core-asl-1.8.8.jar"))).orElse(fail("Not Defined"))
    }
  }

  /**
   * Result:
   *  Error
   */
  "external dep url with forced version" should "throw an error" in withFile() {
    (jsonFile, _) => {
      val resolutionOpt = ResolutionOptions(forceVersion = List("org.apache.commons:commons-compress:1.4.1"))
      val resolveOpt = ResolveOptions(
        resolutionOptions = resolutionOpt
      )
      val options = FetchOptions(jsonOutputFile = jsonFile.getPath, resolveOptions = resolveOpt)
      val params = paramsOrThrow(options)

      val externalUrl = encode("http://central.maven.org/maven2/junit/junit/4.12/junit-4.12.jar", "UTF-8")

      assertThrows[Exception]({
        Fetch.task(
          params,
          pool,
          Seq(
            "org.apache.commons:commons-compress:1.5,url=" + externalUrl
          )
        ).unsafeRun()(ec)
      })
    }
  }

  /**
   * Result:
   * |└─ org.apache.commons:commons-compress:1.5
   */
  "external dep url with the same forced version" should "fetch junit-4.12.jar" in withFile() {
    (jsonFile, _) => {
      val resolutionOpt = ResolutionOptions(forceVersion = List("org.apache.commons:commons-compress:1.5"))
      val resolveOpt = ResolveOptions(
        resolutionOptions = resolutionOpt
      )
      val options = FetchOptions(jsonOutputFile = jsonFile.getPath, resolveOptions = resolveOpt)
      val params = paramsOrThrow(options)

      val externalUrl = encode("http://central.maven.org/maven2/junit/junit/4.12/junit-4.12.jar", "UTF-8")

      Fetch.task(
        params,
        pool,
        Seq(
          "org.apache.commons:commons-compress:1.5,url=" + externalUrl
        )
      ).unsafeRun()(ec)

      val node: ReportNode = getReportFromJson(jsonFile)

      val depNodes: Seq[DepNode] = node.dependencies
      assert(depNodes.length == 1)
      depNodes.head.file.map( f => assert(f.contains("junit/junit/4.12/junit-4.12.jar"))).orElse(fail("Not Defined"))
    }
  }

  /**
   * Result:
   * |└─ org.apache.commons:commons-compress:1.4.1 -> 1.5
   */
  "external dep url on higher version" should "fetch junit-4.12.jar" in withFile() {
    (jsonFile, _) => {
      val options = FetchOptions(jsonOutputFile = jsonFile.getPath)
      val params = paramsOrThrow(options)

      // encode path to different jar than requested
      val externalUrl = encode("http://central.maven.org/maven2/junit/junit/4.12/junit-4.12.jar", "UTF-8")

      Fetch.task(
        params,
        pool,
        Seq(
          "org.apache.commons:commons-compress:1.4.1",
          "org.apache.commons:commons-compress:1.5,url=" + externalUrl
        )
      ).unsafeRun()(ec)

      val node: ReportNode = getReportFromJson(jsonFile)

      val depNodes: Seq[DepNode] = node.dependencies
        .filter(_.coord == "org.apache.commons:commons-compress:1.5")
        .sortBy(fileNameLength)
      assert(depNodes.length == 1)
      depNodes.head.file.map( f => assert(f.contains("junit/junit/4.12/junit-4.12.jar"))).orElse(fail("Not Defined"))
    }
  }

  /**
   * Result:
   * |└─ org.apache.commons:commons-compress:1.4.1 -> 1.5
   * |   └─ org.tukaani:xz:1.2
   */
  "external dep url on lower version" should "fetch higher version" in withFile() {
    (jsonFile, _) => {
      val options = FetchOptions(jsonOutputFile = jsonFile.getPath)
      val params = paramsOrThrow(options)

      // encode path to different jar than requested
      val externalUrl = encode("http://central.maven.org/maven2/junit/junit/4.12/junit-4.12.jar", "UTF-8")

      Fetch.task(
        params,
        pool,
        Seq(
          "org.apache.commons:commons-compress:1.4.1,url=" + externalUrl,
          "org.apache.commons:commons-compress:1.5"
        )
      ).unsafeRun()(ec)

      val node: ReportNode = getReportFromJson(jsonFile)

      val depNode = node.dependencies.find(_.coord == "org.apache.commons:commons-compress:1.5")
      assert(depNode.isDefined)
      depNode.get.file.map( f => assert(f.contains("commons-compress-1.5.jar"))).orElse(fail("Not Defined"))

      assert(depNode.get.dependencies.size == 1)
      assert(depNode.get.dependencies.head.contains("org.tukaani:xz:1.2"))
    }
  }

  "Bad pom resolve" should "succeed with retry" in withTempDir("tmp_dir") {
    dir => {
      def runFetchJunit() = {
        val cacheOpt = CacheOptions(cache = dir.getAbsolutePath)
        val resolveOpt = ResolveOptions(cacheOptions = cacheOpt)
        val options = FetchOptions(resolveOptions = resolveOpt)
        val params = paramsOrThrow(options)
        val (_, files) = Fetch.task(params, pool, Seq("junit:junit:4.12"))
          .unsafeRun()(ec)
        assert(files.map(_._2.getName).toSet
          .equals(Set("junit-4.12.jar", "hamcrest-core-1.3.jar")))
        val junitJarPath = files.map(_._2.getAbsolutePath()).filter(_.contains("junit-4.12.jar"))
          .head
        val junitPomFile = Paths.get(junitJarPath.replace(".jar", ".pom"))
        val junitPomShaFile = Paths.get(junitJarPath.replace(".jar", ".pom.sha1"))
        assert(Files.isRegularFile(junitPomFile))
        assert(Files.isRegularFile(junitPomShaFile))
        junitPomFile
      }

      val junitPomFile = runFetchJunit()
      val originalPomContent = Files.readAllBytes(junitPomFile)

      // Corrupt the pom content
      Files.write(junitPomFile, "bad pom".getBytes(UTF_8))

      // Run fetch again and it should pass because of retrying om the bad pom.
      val pom = runFetchJunit()
      assert(Files.readAllBytes(pom).sameElements(originalPomContent))
    }
  }

  "Bad pom sha-1 resolve" should "succeed with retry" in withTempDir("tmp_dir") {
    dir => {
      def runFetchJunit() = {
        val cacheOpt = CacheOptions(cache = dir.getAbsolutePath)
        val resolveOpt = ResolveOptions(cacheOptions = cacheOpt)
        val options = FetchOptions(resolveOptions = resolveOpt)
        val params = paramsOrThrow(options)
        val (_, files) = Fetch.task(params, pool, Seq("junit:junit:4.12"))
          .unsafeRun()(ec)
        assert(files.map(_._2.getName).toSet
          .equals(Set("junit-4.12.jar", "hamcrest-core-1.3.jar")))
        val junitJarPath = files.map(_._2.getAbsolutePath()).filter(_.contains("junit-4.12.jar"))
          .head
        val junitPomFile = Paths.get(junitJarPath.replace(".jar", ".pom"))
        val junitPomShaFile = Paths.get(junitJarPath.replace(".jar", ".pom.sha1"))
        assert(Files.isRegularFile(junitPomFile))
        assert(Files.isRegularFile(junitPomShaFile))
        junitPomShaFile
      }

      val junitPomSha1File = runFetchJunit()
      val originalShaContent = Files.readAllBytes(junitPomSha1File)

      // Corrupt the pom content
      println(s"Corrupting $junitPomSha1File")
      Files.write(junitPomSha1File, "adc83b19e793491b1c6ea0fd8b46cd9f32e592fc".getBytes(UTF_8))

      // Run fetch again and it should pass because of retrying om the bad pom.
      val sha = runFetchJunit()
      assert(Files.readAllBytes(sha).sameElements(originalShaContent))
    }
  }

  "Bad jar resolve" should "succeed with retry" in withTempDir("tmp_dir") {
    dir => {
      def runFetchJunit() = {
        val cacheOpt = CacheOptions(cache = dir.getAbsolutePath)
        val resolveOpt = ResolveOptions(cacheOptions = cacheOpt)
        val options = FetchOptions(resolveOptions = resolveOpt)
        val params = paramsOrThrow(options)
        val (_, files) = Fetch.task(params, pool, Seq("junit:junit:4.12"))
          .unsafeRun()(ec)
        assert(files.map(_._2.getName).toSet
          .equals(Set("junit-4.12.jar", "hamcrest-core-1.3.jar")))
        val junitJarPath = files.map(_._2.getAbsolutePath()).filter(_.contains("junit-4.12.jar"))
          .head
        Paths.get(junitJarPath)
      }

      val originalJunitJar = runFetchJunit()
      val originalJunitJarContent = Files.readAllBytes(originalJunitJar)

      // Corrupt the jar content
      Files.write(originalJunitJar, "bad jar".getBytes(UTF_8))

      // Run fetch again and it should pass because of retrying on the bad jar.
      val jar = runFetchJunit()
      assert(Files.readAllBytes(jar).sameElements(originalJunitJarContent))
    }
  }

  "Bad jar sha-1 resolve" should "succeed with retry" in withTempDir("tmp_dir") {
    dir => {
      def runFetchJunit() = {
        val cacheOpt = CacheOptions(cache = dir.getAbsolutePath)
        val resolveOpt = ResolveOptions(cacheOptions = cacheOpt)
        val options = FetchOptions(resolveOptions = resolveOpt)
        val params = paramsOrThrow(options)
        val (_, files) = Fetch.task(params, pool, Seq("junit:junit:4.12"))
          .unsafeRun()(ec)
        assert(files.map(_._2.getName).toSet
          .equals(Set("junit-4.12.jar", "hamcrest-core-1.3.jar")))
        val junitJarPath = files.map(_._2.getAbsolutePath()).filter(_.contains("junit-4.12.jar"))
          .head
        val junitJarShaFile = Paths.get(junitJarPath.replace(".jar", ".jar.sha1"))
        assert(Files.isRegularFile(junitJarShaFile))
        junitJarShaFile
      }

      val originalJunitJarSha1 = runFetchJunit()
      val originalJunitJarSha1Content = Files.readAllBytes(originalJunitJarSha1)

      // Corrupt the jar content
      println(s"Corrupting $originalJunitJarSha1")
      Files.write(originalJunitJarSha1, "adc83b19e793491b1c6ea0fd8b46cd9f32e592fc".getBytes(UTF_8))

      // Run fetch again and it should pass because of retrying on the bad jar.
      val jarSha1 = runFetchJunit()
      assert(Files.readAllBytes(jarSha1).sameElements(originalJunitJarSha1Content))
    }
  }

  "Wrong range partial artifact resolve" should "succeed with retry" in withTempDir("tmp_dir") {
    dir => {
      def runFetchJunit() = {
        val cacheOpt = CacheOptions(mode = "force", cache = dir.getAbsolutePath)
        val resolveOpt = ResolveOptions(cacheOptions = cacheOpt)
        val options = FetchOptions(resolveOptions = resolveOpt)
        val params = paramsOrThrow(options)
        val (_, files) = Fetch.task(params, pool, Seq("junit:junit:4.6"))
          .unsafeRun()(ec)
        assert(files.map(_._2.getName).toSet
          .equals(Set("junit-4.6.jar")))
        val junitJarPath = files.map(_._2.getAbsolutePath()).filter(_.contains("junit-4.6.jar"))
          .head
        Paths.get(junitJarPath)
      }

      val originalJunitJar = runFetchJunit()

      val originalJunitJarContent = Files.readAllBytes(originalJunitJar)

      // Move the jar to partial (but complete) download
      val newJunitJar = originalJunitJar.getParent.resolve(originalJunitJar.getFileName.toString + ".part")
      Files.move(originalJunitJar, newJunitJar)

      // Run fetch again and it should pass because of retrying on the partial jar.
      val jar = runFetchJunit()
      assert(Files.readAllBytes(jar).sameElements(originalJunitJarContent))
    }
  }

  it should "fail because of resolution" in {
    val options = FetchOptions()
    val params = paramsOrThrow(options)
    val a = Fetch.task(params, pool, Seq("sh.almond:scala-kernel_2.12.8:0.2.2"))
      .attempt
      .unsafeRun()(ec)

    a match {
      case Right(_) =>
        throw new Exception("should have failed")
      case Left(_: ResolveException) =>
      case Left(ex) =>
        throw new Exception("Unexpected exception type", ex)
    }
  }

  it should "fail to resolve, but try to fetch artifacts anyway" in {
    val artifactOptions = ArtifactOptions(
      forceFetch = true
    )
    val options = FetchOptions(
      artifactOptions = artifactOptions
    )
    val params = paramsOrThrow(options)
    val (_, l) = Fetch.task(params, pool, Seq("sh.almond:scala-kernel_2.12.8:0.2.2"))
      .unsafeRun()(ec)

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
      "org/wildfly/common/wildfly-common/1.3.0.Beta1/wildfly-common-1.3.0.Beta1.jar",
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
}
