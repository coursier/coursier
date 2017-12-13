package coursier.cli

import java.io.{File, FileWriter}

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
    val commonOpt = CommonOptions(softExcludeFile = file.getAbsolutePath)
    val fetchOpt = FetchOptions(common = commonOpt)

    trait ExtraArgsApp extends caseapp.core.DefaultArgsApp {
      override def remainingArgs: Seq[String] = Seq("junit:junit:4.12")
    }

    val fetch = new Fetch(fetchOpt) with ExtraArgsApp
    fetch.apply()
    val filesFetched = fetch.files0.map(_.getName).toSet
    val expected = Set("junit-4.12.jar")
    assert(filesFetched.equals(expected), s"files fetched: $filesFetched not matching expected: $expected")
  }

}
