package coursier.cli

import java.io.{File, FileWriter}

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import coursier.cli.util.ReportNode
import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class CliIntegrationTest extends FlatSpec {

  def withFile(content: String)(testCode: (File, FileWriter) => Any) {
    val file = File.createTempFile("hello", "world") // create the fixture
    val writer = new FileWriter(file)
    writer.write(content)
    writer.flush()
    try {
      testCode(file, writer) // "loan" the fixture to the test
    }
    finally {
      writer.close()
      file.delete()
    }
  }

  "Normal fetch" should "get all files" in {

    val fetchOpt = FetchOptions(common = CommonOptions())

    trait ExtraArgsApp extends caseapp.core.DefaultArgsApp {
      private var remainingArgs1 = Seq.empty[String]
      private var extraArgs1 = Seq.empty[String]

      override def setRemainingArgs(remainingArgs: Seq[String], extraArgs: Seq[String]): Unit = {
        remainingArgs1 = remainingArgs
        extraArgs1 = extraArgs
      }

      override def remainingArgs: Seq[String] = Seq("junit:junit:4.12")

      def extraArgs: Seq[String] =
        extraArgs1
    }

    val fetch = new Fetch(fetchOpt) with ExtraArgsApp
    fetch.apply()
    assert(fetch.files0.map(_.getName).toSet.equals(Set("junit-4.12.jar", "hamcrest-core-1.3.jar")))

  }

  "Module level" should "exclude correctly" in withFile(
    "junit:junit--org.hamcrest:hamcrest-core") { (file, writer) =>
    withFile("") { (f2, w2) =>
      val commonOpt = CommonOptions(softExcludeFile = file.getAbsolutePath, jsonOutputFile = f2.getCanonicalPath)
      val fetchOpt = FetchOptions(common = commonOpt)

      trait ExtraArgsApp extends caseapp.core.DefaultArgsApp {
        override def remainingArgs: Seq[String] = Seq("junit:junit:4.12")
      }

      val fetch = new Fetch(fetchOpt) with ExtraArgsApp
      fetch.apply()
      val filesFetched = fetch.files0.map(_.getName).toSet
      val expected = Set("junit-4.12.jar")
      assert(filesFetched.equals(expected), s"files fetched: $filesFetched not matching expected: $expected")

      // Parse back the output json file
      val source = scala.io.Source.fromFile(f2.getCanonicalPath)
      val str = try source.mkString finally source.close()

      def objectMapper = {
        val mapper = new ObjectMapper with ScalaObjectMapper
        mapper.registerModule(DefaultScalaModule)
        mapper
      }

      val node: ReportNode = objectMapper.readValue[ReportNode](str)

      assert(node.dependencies.length == 1)
      assert(node.dependencies.head.coord == "junit:junit:4.12")
    }

  }

  /**
    * Result without exclusion:
    * |└─ org.apache.avro:avro:1.7.4
    * |├─ com.thoughtworks.paranamer:paranamer:2.3
    * |├─ org.apache.commons:commons-compress:1.4.1
    * |│  └─ org.tukaani:xz:1.0
    * |├─ org.codehaus.jackson:jackson-core-asl:1.8.8
    * |├─ org.codehaus.jackson:jackson-mapper-asl:1.8.8
    * |│  └─ org.codehaus.jackson:jackson-core-asl:1.8.8
    * |├─ org.slf4j:slf4j-api:1.6.4
    * |└─ org.xerial.snappy:snappy-java:1.0.4.1
    */
  "avro exclude xz" should "not fetch xz" in withFile(
    "org.apache.avro:avro--org.tukaani:xz") { (file, writer) =>
    val commonOpt = CommonOptions(softExcludeFile = file.getAbsolutePath)
    val fetchOpt = FetchOptions(common = commonOpt)

    trait ExtraArgsApp extends caseapp.core.DefaultArgsApp {
      override def remainingArgs: Seq[String] = Seq("org.apache.avro:avro:1.7.4")
    }

    val fetch = new Fetch(fetchOpt) with ExtraArgsApp
    fetch.apply()
    val filesFetched = fetch.files0.map(_.getName).toSet
    assert(!filesFetched.contains("xz-1.0.jar"))
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
    "org.apache.avro:avro--org.tukaani:xz") { (file, writer) =>
    val commonOpt = CommonOptions(softExcludeFile = file.getAbsolutePath)
    val fetchOpt = FetchOptions(common = commonOpt)

    trait ExtraArgsApp extends caseapp.core.DefaultArgsApp {
      override def remainingArgs: Seq[String] = Seq("org.apache.avro:avro:1.7.4", "org.apache.commons:commons-compress:1.4.1")
    }

    val fetch = new Fetch(fetchOpt) with ExtraArgsApp
    fetch.apply()
    val filesFetched = fetch.files0.map(_.getName).toSet
    assert(filesFetched.contains("xz-1.0.jar"))
  }

}
