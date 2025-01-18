package coursier.tests

import coursier.core.{Info, Versions}
import coursier.core.compatibility._
import coursier.ivy.IvyXml
import utest._

object IvyXmlParsingTests extends TestSuite {
  val tests = Tests {
    test("infoWithHomePage") {
      // slice of https://dl.bintray.com/sbt/sbt-plugin-releases/com.github.gseitz/sbt-release/scala_2.12/sbt_1.0/1.0.12/ivys/ivy.xml
      val node =
        """
        <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
          <info organisation="com.github.gseitz" module="sbt-release" revision="1.0.12" status="release" publication="20191016122629" e:sbtVersion="1.0" e:scalaVersion="2.12">
            <license name="Apache-2.0" url="http://www.apache.org/licenses/LICENSE-2.0"/>
            <description homepage="https://github.com/sbt/sbt-release">
            sbt-release
            </description>
          </info>
        </ivy-module>
      """

      val result = IvyXml.project(xmlParseDom(node).toOption.get).map(_.info)
      val expected = Right(Info(
        "sbt-release",
        "https://github.com/sbt/sbt-release",
        List(("Apache-2.0", Some("http://www.apache.org/licenses/LICENSE-2.0"))),
        Nil,
        Some(Versions.DateTime(2019, 10, 16, 12, 26, 29)),
        None
      ))

      assert(result == expected)
    }

    test("infoWithoutHomePage") {
      val node =
        """
        <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
          <info organisation="com.github.gseitz" module="sbt-release" revision="1.0.12" status="release" e:sbtVersion="1.0" e:scalaVersion="2.12">
            <description>
            sbt-release
            </description>
          </info>
        </ivy-module>
      """

      val result   = IvyXml.project(xmlParseDom(node).toOption.get).map(_.info)
      val expected = Right(Info("sbt-release", "", Nil, Nil, None, None))

      assert(result == expected)
    }

    test("infoWithExtraInfo") {
      val node =
        """
        <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
          <info organisation="com.github.gseitz" module="sbt-release" revision="1.0.12" status="release"
          e:sbtVersion="1.0" e:scalaVersion="2.12" e:info.versionScheme="semver-spec">
          </info>
        </ivy-module>
      """

      val result   = IvyXml.project(xmlParseDom(node).toOption.get).map(_.properties)
      val expected = Right(Seq("info.versionScheme" -> "semver-spec"))

      assert(result == expected)
    }

    test("'/' and '\\' are invalid in organisation") {
      val node =
        """
        <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
          <info organisation="com\github\gseitz" module="sbt-release" revision="1.0.12" status="release" publication="20191016122629" e:sbtVersion="1.0" e:scalaVersion="2.12">
          </info>
        </ivy-module>
      """

      val failure = IvyXml.project(xmlParseDom(node).toOption.get)

      assert(failure.isLeft)
      val message = failure.left.toOption.get
      assert(message.contains("com\\github\\gseitz"))
    }

    test("'/' and '\\' are invalid in module") {
      val node =
        """
        <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
          <info organisation="com.github.gseitz" module="sbt/release" revision="1.0.12" status="release" publication="20191016122629" e:sbtVersion="1.0" e:scalaVersion="2.12">
          </info>
        </ivy-module>
      """

      val failure = IvyXml.project(xmlParseDom(node).toOption.get)

      assert(failure.isLeft)
      val message = failure.left.toOption.get
      assert(message.contains("sbt/release"))
    }

    test("'/' and '\\' are invalid in revision") {
      val node =
        """
        <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
          <info organisation="com.github.gseitz" module="sbt.release" revision="1.0.12/SNAPSHOT" status="release" publication="20191016122629" e:sbtVersion="1.0" e:scalaVersion="2.12">
          </info>
        </ivy-module>
      """

      val failure = IvyXml.project(xmlParseDom(node).toOption.get)

      assert(failure.isLeft)
      val message = failure.left.toOption.get
      assert(message.contains("1.0.12/SNAPSHOT"))
    }
  }
}
