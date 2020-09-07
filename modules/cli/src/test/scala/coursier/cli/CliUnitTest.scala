package coursier.cli

import java.io.{File, FileWriter}

import coursier.moduleString
import coursier.cli.options.DependencyOptions
import coursier.cli.params.DependencyParams
import coursier.parse.JavaOrScalaModule
import utest._

class CliUnitTest extends TestSuite {

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

  val tests = Tests {
  test("Normal text should parse correctly") - withFile(
    "org1:name1--org2:name2") { (file, _) =>
    val options = DependencyOptions(localExcludeFile = file.getAbsolutePath)
    val params = DependencyParams(options, None)
      .fold(e => sys.error(e.toString), identity)
    val expected = Map(JavaOrScalaModule.JavaModule(mod"org1:name1") -> Set(JavaOrScalaModule.JavaModule(mod"org2:name2")))
    Predef.assert(params.perModuleExclude.equals(expected), s"got ${params.perModuleExclude}")
  }

  test("Multiple excludes should be combined") - withFile(
    "org1:name1--org2:name2\n" +
      "org1:name1--org3:name3\n" +
      "org4:name4--org5:name5") { (file, _) =>

    val options = DependencyOptions(localExcludeFile = file.getAbsolutePath)
    val params = DependencyParams(options, None)
      .fold(e => sys.error(e.toString), identity)
    val expected = Map(
      JavaOrScalaModule.JavaModule(mod"org1:name1") -> Set(JavaOrScalaModule.JavaModule(mod"org2:name2"), JavaOrScalaModule.JavaModule(mod"org3:name3")),
      JavaOrScalaModule.JavaModule(mod"org4:name4") -> Set(JavaOrScalaModule.JavaModule(mod"org5:name5"))
    )
    assert(params.perModuleExclude.equals(expected))
  }

  test("extra -- should error") - withFile(
    "org1:name1--org2:name2--xxx\n" +
      "org1:name1--org3:name3\n" +
      "org4:name4--org5:name5") { (file, _) =>
    val options = DependencyOptions(localExcludeFile = file.getAbsolutePath)
    DependencyParams(options, None).toEither match {
      case Left(errors) =>
        assert(errors.exists(_.startsWith("Failed to parse ")))
      case Right(p) =>
        sys.error(s"Should have errored (got $p)")
    }
  }

  test("child has no name should error") - withFile(
    "org1:name1--org2:") { (file, _) =>
    val options = DependencyOptions(localExcludeFile = file.getAbsolutePath)
    DependencyParams(options, None).toEither match {
      case Left(errors) =>
        assert(errors.exists(_.startsWith("Failed to parse ")))
      case Right(p) =>
        sys.error(s"Should have errored (got $p)")
    }
  }

  test("child has nothing should error") - withFile(
    "org1:name1--:") { (file, _) =>
    val options = DependencyOptions(localExcludeFile = file.getAbsolutePath)
    DependencyParams(options, None).toEither match {
      case Left(errors) =>
        assert(errors.exists(_.startsWith("Failed to parse ")))
      case Right(p) =>
        sys.error(s"Should have errored (got $p)")
    }
  }
  }
}
