package coursier.cli

import java.io.{File, FileWriter}

import coursier.{moduleNameString, organizationString}
import coursier.cli.options.CommonOptions
import coursier.cli.options.shared.DependencyOptions
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
    "org1:name1--org2:name2") { (file, writer) =>
    val dependencyOpt = DependencyOptions(localExcludeFile = file.getAbsolutePath)
    val opt = CommonOptions(dependencyOptions = dependencyOpt)
    val helper = new Helper(opt, Seq())
    assert(helper.localExcludeMap.equals(Map("org1:name1" -> Set((org"org2", name"name2")))))
  }

  "Multiple excludes" should "be combined" in withFile(
    "org1:name1--org2:name2\n" +
      "org1:name1--org3:name3\n" +
      "org4:name4--org5:name5") { (file, writer) =>

    val dependencyOpt = DependencyOptions(localExcludeFile = file.getAbsolutePath)
    val opt = CommonOptions(dependencyOptions = dependencyOpt)
    val helper = new Helper(opt, Seq())
    assert(helper.localExcludeMap.equals(Map(
      "org1:name1" -> Set((org"org2", name"name2"), (org"org3", name"name3")),
      "org4:name4" -> Set((org"org5", name"name5")))))
  }

  "extra --" should "error" in withFile(
    "org1:name1--org2:name2--xxx\n" +
      "org1:name1--org3:name3\n" +
      "org4:name4--org5:name5") { (file, writer) =>
    assertThrows[SoftExcludeParsingException]({
      val dependencyOpt = DependencyOptions(localExcludeFile = file.getAbsolutePath)
      val opt = CommonOptions(dependencyOptions = dependencyOpt)
      new Helper(opt, Seq())
    })
  }

  "child has no name" should "error" in withFile(
    "org1:name1--org2:") { (file, writer) =>
    assertThrows[SoftExcludeParsingException]({
      val dependencyOpt = DependencyOptions(localExcludeFile = file.getAbsolutePath)
      val opt = CommonOptions(dependencyOptions = dependencyOpt)
      new Helper(opt, Seq())
    })
  }

  "child has nothing" should "error" in withFile(
    "org1:name1--:") { (file, writer) =>
    assertThrows[SoftExcludeParsingException]({
      val dependencyOpt = DependencyOptions(localExcludeFile = file.getAbsolutePath)
      val opt = CommonOptions(dependencyOptions = dependencyOpt)
      new Helper(opt, Seq())
    })
  }

}
