package coursier.cli

import java.io.{File, FileWriter}

import coursier.{moduleNameString, organizationString}
import coursier.cli.options.DependencyOptions
import coursier.cli.params.DependencyParams
import org.junit.runner.RunWith
import org.scalatest.FlatSpec
import org.scalatestplus.junit.JUnitRunner


@RunWith(classOf[JUnitRunner])
class CliUnitTest extends FlatSpec {

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

  "Normal text" should "parse correctly" in withFile(
    "org1:name1--org2:name2") { (file, _) =>
    val options = DependencyOptions(localExcludeFile = file.getAbsolutePath)
    val params = DependencyParams("2.12.8", options)
      .fold(e => sys.error(e.toString), identity)
    assert(params.perModuleExclude.equals(Map("org1:name1" -> Set((org"org2", name"name2")))))
  }

  "Multiple excludes" should "be combined" in withFile(
    "org1:name1--org2:name2\n" +
      "org1:name1--org3:name3\n" +
      "org4:name4--org5:name5") { (file, _) =>

    val options = DependencyOptions(localExcludeFile = file.getAbsolutePath)
    val params = DependencyParams("2.12.8", options)
      .fold(e => sys.error(e.toString), identity)
    assert(params.perModuleExclude.equals(Map(
      "org1:name1" -> Set((org"org2", name"name2"), (org"org3", name"name3")),
      "org4:name4" -> Set((org"org5", name"name5")))))
  }

  "extra --" should "error" in withFile(
    "org1:name1--org2:name2--xxx\n" +
      "org1:name1--org3:name3\n" +
      "org4:name4--org5:name5") { (file, _) =>
    val options = DependencyOptions(localExcludeFile = file.getAbsolutePath)
    DependencyParams("2.12.8", options).toEither match {
      case Left(errors) =>
        assert(errors.exists(_.startsWith("Failed to parse ")))
      case Right(p) =>
        sys.error(s"Should have errored (got $p)")
    }
  }

  "child has no name" should "error" in withFile(
    "org1:name1--org2:") { (file, _) =>
    val options = DependencyOptions(localExcludeFile = file.getAbsolutePath)
    DependencyParams("2.12.8", options).toEither match {
      case Left(errors) =>
        assert(errors.exists(_.startsWith("Failed to parse ")))
      case Right(p) =>
        sys.error(s"Should have errored (got $p)")
    }
  }

  "child has nothing" should "error" in withFile(
    "org1:name1--:") { (file, _) =>
    val options = DependencyOptions(localExcludeFile = file.getAbsolutePath)
    DependencyParams("2.12.8", options).toEither match {
      case Left(errors) =>
        assert(errors.exists(_.startsWith("Failed to parse ")))
      case Right(p) =>
        sys.error(s"Should have errored (got $p)")
    }
  }

}
