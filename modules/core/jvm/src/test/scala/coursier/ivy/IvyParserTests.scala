package coursier.ivy

import java.io.File

import coursier.core._
import utest._
import java.nio.file.Files
import scala.util.Using
import scala.io.Source
import scala.util._

object IvyParserTests extends TestSuite {
  val ivyXmlPath = Option(getClass.getResource("/sample-jackson-databind-ivy.xml"))
    .map(u => new File(u.toURI).getAbsolutePath)
    .getOrElse {
      throw new Exception("sample-jackson-databind-ivy.xml resource not found")
    }

  val ivyXmlFile = new File(ivyXmlPath)

  val result = Using(Source.fromFile(ivyXmlFile)) { bufferedSource =>
    val contents = bufferedSource.getLines()
      .map(line => s"$line\n")
      .mkString

    compatibility.xmlParse(contents)
  } match {
    case Failure(exception)        => throw exception
    case Success(Left(parseError)) => throw new IllegalArgumentException(parseError)
    case Success(Right(xmlNode))   => xmlNode
  }

  val tests: Tests = Tests {
    test("project can be parsed") {
      val success = IvyXml.project(result)
      assert(success.isRight)
    }

    test("licenses are available") {
      val success = IvyXml.project(result)
      assert(success.isRight)
      val project = success.toOption.get
      assert(project.info.licenseInfo.exists(license =>
        license.name == "The Apache Software License, Version 2.0" &&
        license.url == Some("http://www.apache.org/licenses/LICENSE-2.0.txt")
      ))
    }
  }
}
