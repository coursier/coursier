package coursier.cli

import java.io._

import java.net.URLEncoder.encode
import argonaut.Argonaut._
import caseapp.core.RemainingArgs
import coursier.cli.options._
import coursier.cli.util.{DepNode, ReportNode}
import coursier.internal.FileUtil
import java.io._
import java.net.URLEncoder.encode
import java.nio.charset.StandardCharsets.UTF_8
import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import scala.io.Source

@RunWith(classOf[JUnitRunner])
class CliFetchIntegrationTest extends FlatSpec with CliTestLib {

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

    val fetchOpt = FetchOptions(common = CommonOptions())
    val fetch = Fetch(fetchOpt, RemainingArgs(Seq("junit:junit:4.12"), Seq()))
    assert(fetch.files0.map(_.getName).toSet.equals(Set("junit-4.12.jar", "hamcrest-core-1.3.jar")))

  }

  "Module level" should "exclude correctly" in withFile(
    "junit:junit--org.hamcrest:hamcrest-core") { (file, _) =>
    withFile() { (jsonFile, _) =>
      val commonOpt = CommonOptions(localExcludeFile = file.getAbsolutePath, jsonOutputFile = jsonFile.getPath)
      val fetchOpt = FetchOptions(common = commonOpt)

      val fetch = Fetch(fetchOpt, RemainingArgs(Seq("junit:junit:4.12"), Seq()))
      val filesFetched = fetch.files0.map(_.getName).toSet
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
      val commonOpt = CommonOptions(localExcludeFile = file.getAbsolutePath, jsonOutputFile = jsonFile.getPath)
      val fetchOpt = FetchOptions(common = commonOpt)

      val fetch = Fetch(fetchOpt, RemainingArgs(Seq("org.apache.avro:avro:1.7.4"), Seq()))

      val filesFetched = fetch.files0.map(_.getName).toSet
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
          val commonOpt = CommonOptions(localExcludeFile = file.getAbsolutePath, jsonOutputFile = jsonFile.getPath)
          val fetchOpt = FetchOptions(common = commonOpt)

          val fetch = Fetch(fetchOpt, RemainingArgs(Seq("org.apache.avro:avro:1.7.4", "org.apache.commons:commons-compress:1.4.1"), Seq()))
          val filesFetched = fetch.files0.map(_.getName).toSet
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
          val commonOpt = CommonOptions(jsonOutputFile = jsonFile.getPath)
          val fetchOpt = FetchOptions(common = commonOpt)

          Fetch.run(fetchOpt, RemainingArgs(Seq("org.apache.commons:commons-compress:1.4.1", "org.tukaani:xz:1.1"), Seq()))

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
          val commonOpt = CommonOptions(jsonOutputFile = jsonFile.getPath)
          val fetchOpt = FetchOptions(common = commonOpt)

          Fetch.run(fetchOpt, RemainingArgs(Seq("org.apache.commons:commons-compress:1.5", "org.tukaani:xz:1.1"), Seq()))

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
          val commonOpt = CommonOptions(jsonOutputFile = jsonFile.getPath)
          val fetchOpt = FetchOptions(common = commonOpt)

          Fetch.run(
            fetchOpt,
            RemainingArgs(Seq("org.apache.commons:commons-compress:1.5,classifier=tests"), Seq())
          )

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
          val commonOpt = CommonOptions(jsonOutputFile = jsonFile.getPath)
          val fetchOpt = FetchOptions(common = commonOpt)

          Fetch.run(
            fetchOpt,
            RemainingArgs(
              Seq(
                "org.apache.commons:commons-compress:1.5,classifier=tests",
                "org.apache.commons:commons-compress:1.5"
              ),
              Seq()
            )
          )

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
          val commonOpt = CommonOptions(jsonOutputFile = jsonFile.getPath, intransitive = List("org.apache.commons:commons-compress:1.5"))
          val fetchOpt = FetchOptions(common = commonOpt)

          Fetch.run(fetchOpt, RemainingArgs(Nil, Nil))

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
          val commonOpt = CommonOptions(jsonOutputFile = jsonFile.getPath, intransitive = List("org.apache.commons:commons-compress:1.5,classifier=tests"))
          val fetchOpt = FetchOptions(common = commonOpt)

          Fetch.run(fetchOpt, RemainingArgs(Seq(), Seq()))

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
          val commonOpt = CommonOptions(jsonOutputFile = jsonFile.getPath, forceVersion = List("org.apache.commons:commons-compress:1.4.1"))
          val fetchOpt = FetchOptions(common = commonOpt)

          Fetch(
            fetchOpt,
            RemainingArgs(Seq("org.apache.commons:commons-compress:1.5,classifier=tests"), Seq())
          )

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
          val commonOpt = CommonOptions(jsonOutputFile = jsonFile.getPath,
            intransitive = List("org.apache.commons:commons-compress:1.5,classifier=tests"),
            forceVersion = List("org.apache.commons:commons-compress:1.4.1"))
          val fetchOpt = FetchOptions(common = commonOpt)

          Fetch.run(fetchOpt, RemainingArgs(Seq(), Seq()))

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
      val commonOpt = CommonOptions(
        jsonOutputFile = jsonFile.getPath,
        profile = List("scala-2.10", "!scala-2.11")
      )
      val fetchOpt = FetchOptions(common = commonOpt)

      Fetch(
        fetchOpt,
        RemainingArgs(Seq("org.apache.spark:spark-core_2.10:2.2.1"), Seq())
      )

      val node = getReportFromJson(jsonFile)

      assert(node.dependencies.exists(_.coord.startsWith("org.scala-lang:scala-library:2.10.")))
      assert(!node.dependencies.exists(_.coord.startsWith("org.scala-lang:scala-library:2.11.")))
  }

  "com.spotify:helios-testing:0.9.193" should "have dependencies with classifiers" in withFile() {
    (excludeFile, _) =>
      withFile() {
        (jsonFile, _) => {
          val commonOpt = CommonOptions(jsonOutputFile = jsonFile.getPath)
          val fetchOpt = FetchOptions(common = commonOpt)

          val heliosCoord = "com.spotify:helios-testing:0.9.193"

          Fetch(
            fetchOpt,
            RemainingArgs(Seq(heliosCoord), Seq())
          )
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
   * |└─ org.apache.commons:commons-compress:1.5
   */
  "local dep url" should "have coursier-fetch-test.jar" in withFile() {
    (jsonFile, _) => {
      withFile("tada", "coursier-fetch-test", ".jar") {
        (testFile, _) => {
          val path = testFile.getAbsolutePath
          val encodedUrl = encode("file://" + path, "UTF-8")


          val commonOpt = CommonOptions(jsonOutputFile = jsonFile.getPath)
          val fetchOpt = FetchOptions(common = commonOpt)

          // fetch with encoded url set to temp jar
          Fetch.run(
            fetchOpt,
            RemainingArgs(
              Seq(
                "org.apache.commons:commons-compress:1.5,url=" + encodedUrl
              ),
              Seq()
            )
          )

          val node: ReportNode = getReportFromJson(jsonFile)

          val depNodes: Seq[DepNode] = node.dependencies
            .filter(_.coord == "org.apache.commons:commons-compress:1.5")
            .sortBy(fileNameLength)
          assert(depNodes.length == 1)

          val urlInJsonFile = depNodes.head.file.get
          assert(urlInJsonFile.contains(path))

          // open jar and inspect contents
          val fileContents = Source.fromFile(urlInJsonFile).getLines.mkString
          assert(fileContents == "tada")
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
      val commonOpt = CommonOptions(jsonOutputFile = jsonFile.getPath)
      val fetchOpt = FetchOptions(common = commonOpt)

      // encode path to different jar than requested
      val externalUrl = encode("http://central.maven.org/maven2/junit/junit/4.12/junit-4.12.jar", "UTF-8")

      // fetch with different artifact url
      Fetch.run(
        fetchOpt,
        RemainingArgs(
          Seq(
            "org.apache.commons:commons-compress:1.5,url=" + externalUrl
          ),
          Seq()
        )
      )

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
   * |└─ a:b:c
   */
  "external dep url with arbitrary coords" should "fetch junit-4.12.jar" in withFile() {
    (jsonFile, _) => {
      val commonOpt = CommonOptions(jsonOutputFile = jsonFile.getPath)
      val fetchOpt = FetchOptions(common = commonOpt)

      // encode path to different jar than requested
      val externalUrl = encode("http://central.maven.org/maven2/junit/junit/4.12/junit-4.12.jar", "UTF-8")

      // arbitrary coords fail to fetch because... coords need to exist in a repo somewhere to work. fix this.
      Fetch.run(
        fetchOpt,
        RemainingArgs(
          Seq(
            "a:b:c,url=" + externalUrl
          ),
          Seq()
        )
      )

      val node: ReportNode = getReportFromJson(jsonFile)

      val depNodes: Seq[DepNode] = node.dependencies
        .filter(_.coord == "a:b:c")
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
      val commonOpt = CommonOptions(jsonOutputFile = jsonFile.getPath)
      val fetchOpt = FetchOptions(common = commonOpt)

      // encode path to different jar than requested
      val externalUrl = encode("http://central.maven.org/maven2/junit/junit/4.12/junit-4.12.jar", "UTF-8")

      Fetch.run(
        fetchOpt,
        RemainingArgs(
          Seq(
            "org.apache.commons:commons-compress:1.5,url=" + externalUrl + ",classifier=tests"
          ),
          Seq()
        )
      )

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
      val commonOpt = CommonOptions(jsonOutputFile = jsonFile.getPath)
      val fetchOpt = FetchOptions(common = commonOpt)

      // encode path to different jar than requested
      val externalUrl = encode("http://central.maven.org/maven2/junit/junit/4.12/junit-4.12.jar", "UTF-8")

      Fetch.run(
        fetchOpt,
        RemainingArgs(
          Seq(
            "org.apache.commons:commons-compress:1.5",
            "org.tukaani:xz:1.2,classifier=tests,url="+externalUrl
          ),
          Seq()
        )
      )

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
      val commonOpt = CommonOptions(jsonOutputFile = jsonFile.getPath)
      val fetchOpt = FetchOptions(common = commonOpt, sources=true)

      // encode path to different jar than requested

      Fetch.run(
        fetchOpt,
        RemainingArgs(
          Seq(
            "org.apache.commons:commons-compress:1.5,classifier=sources"
          ),
          Seq()
        )
      )
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
      val commonOpt = CommonOptions(jsonOutputFile = jsonFile.getPath)
      val fetchOpt = FetchOptions(common = commonOpt)

      val externalUrl = encode("http://central.maven.org/maven2/junit/junit/4.12/junit-4.12.jar", "UTF-8")

      Fetch.run(
        fetchOpt,
        RemainingArgs(
          Seq(
            "org.apache.commons:commons-compress:1.5,url=" + externalUrl,
            "org.codehaus.jackson:jackson-mapper-asl:1.8.8"
          ),
          Seq()
        )
      )

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
      val commonOpt = CommonOptions(
        jsonOutputFile = jsonFile.getPath,
        forceVersion = List("org.apache.commons:commons-compress:1.4.1"))
      val fetchOpt = FetchOptions(common = commonOpt)

      val externalUrl = encode("http://central.maven.org/maven2/junit/junit/4.12/junit-4.12.jar", "UTF-8")

      assertThrows[Exception]({
        Fetch.run(
          fetchOpt,
          RemainingArgs(
            Seq(
              "org.apache.commons:commons-compress:1.5,url=" + externalUrl
            ),
            Seq()
          )
        )
      })
    }
  }

  /**
   * Result:
   * |└─ org.apache.commons:commons-compress:1.5
   */
  "external dep url with the same forced version" should "fetch junit-4.12.jar" in withFile() {
    (jsonFile, _) => {
      val commonOpt = CommonOptions(
        jsonOutputFile = jsonFile.getPath,
        forceVersion = List("org.apache.commons:commons-compress:1.5"))
      val fetchOpt = FetchOptions(common = commonOpt)

      val externalUrl = encode("http://central.maven.org/maven2/junit/junit/4.12/junit-4.12.jar", "UTF-8")

      Fetch.run(
        fetchOpt,
        RemainingArgs(
          Seq(
            "org.apache.commons:commons-compress:1.5,url=" + externalUrl
          ),
          Seq()
        )
      )

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
      val commonOpt = CommonOptions(jsonOutputFile = jsonFile.getPath)
      val fetchOpt = FetchOptions(common = commonOpt)

      // encode path to different jar than requested
      val externalUrl = encode("http://central.maven.org/maven2/junit/junit/4.12/junit-4.12.jar", "UTF-8")

      Fetch.run(
        fetchOpt,
        RemainingArgs(
          Seq(
            "org.apache.commons:commons-compress:1.4.1",
            "org.apache.commons:commons-compress:1.5,url=" + externalUrl
          ),
          Seq()
        )
      )

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
      val commonOpt = CommonOptions(jsonOutputFile = jsonFile.getPath)
      val fetchOpt = FetchOptions(common = commonOpt)

      // encode path to different jar than requested
      val externalUrl = encode("http://central.maven.org/maven2/junit/junit/4.12/junit-4.12.jar", "UTF-8")

      Fetch.run(
        fetchOpt,
        RemainingArgs(
          Seq(
            "org.apache.commons:commons-compress:1.4.1,url=" + externalUrl,
            "org.apache.commons:commons-compress:1.5"
          ),
          Seq()
        )
      )

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
      def runFetchJunit = {
        val fetchOpt = FetchOptions(common = CommonOptions(cacheOptions = CacheOptions(cache = dir.getAbsolutePath)))
        val fetch = Fetch(fetchOpt, RemainingArgs(Seq("junit:junit:4.12"), Seq()))
        assert(fetch.files0.map(_.getName).toSet
          .equals(Set("junit-4.12.jar", "hamcrest-core-1.3.jar")))
        val junitJarPath = fetch.files0.map(_.getAbsolutePath()).filter(_.contains("junit-4.12.jar"))
          .head
        val junitPomFile = new File(junitJarPath.replace(".jar", ".pom"))
        val junitPomShaFile = new File(junitJarPath.replace(".jar", ".pom.sha1"))
        assert(junitPomFile.exists())
        assert(junitPomShaFile.exists())
        junitPomFile
      }

      val junitPomFile: File = runFetchJunit
      val originalPomContent = FileUtil.readAllBytes(junitPomFile)

      // Corrupt the pom content
      FileUtil.write(junitPomFile, "bad pom".getBytes(UTF_8))

      // Run fetch again and it should pass because of retrying om the bad pom.
      val pom = runFetchJunit
      assert(FileUtil.readAllBytes(pom).sameElements(originalPomContent))
    }
  }

  "Bad jar resolve" should "succeed with retry" in withTempDir("tmp_dir") {
    dir => {
      def runFetchJunit = {
        val fetchOpt = FetchOptions(common = CommonOptions(cacheOptions = CacheOptions(cache = dir.getAbsolutePath)))
        val fetch = Fetch(fetchOpt, RemainingArgs(Seq("junit:junit:4.12"), Seq()))
        assert(fetch.files0.map(_.getName).toSet
          .equals(Set("junit-4.12.jar", "hamcrest-core-1.3.jar")))
        val junitJarPath = fetch.files0.map(_.getAbsolutePath()).filter(_.contains("junit-4.12.jar"))
          .head
        val junitJarFile = new File(junitJarPath)
        junitJarFile
      }

      val originalJunitJar: File = runFetchJunit
      val originalJunitJarContent = FileUtil.readAllBytes(originalJunitJar)

      // Corrupt the jar content
      FileUtil.write(originalJunitJar, "bad jar".getBytes(UTF_8))

      // Run fetch again and it should pass because of retrying on the bad jar.
      val jar = runFetchJunit
      assert(FileUtil.readAllBytes(jar).sameElements(originalJunitJarContent))
    }
  }

}
