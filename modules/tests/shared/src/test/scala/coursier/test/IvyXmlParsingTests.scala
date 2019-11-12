package coursier
package test

import coursier.core.Versions
import coursier.core.compatibility._
import coursier.ivy.IvyXml
import utest._

object IvyXmlParsingTests extends TestSuite {
  val tests = Tests {
    'infoWithHomePage {
      val node = """
        <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
          <info organisation="com.github.gseitz" module="sbt-release" revision="1.0.12" status="release" publication="20191016122629" e:sbtVersion="1.0" e:scalaVersion="2.12">
            <license name="Apache-2.0" url="http://www.apache.org/licenses/LICENSE-2.0"/>
            <description homepage="https://github.com/sbt/sbt-release">
            sbt-release
            </description>
          </info>
        </ivy-module>
      """

      val result = IvyXml.project(xmlParseDom(node).right.get).map(_.info)
      val expected = Right(Info("sbt-release", "https://github.com/sbt/sbt-release",
        List(("Apache-2.0", Some("http://www.apache.org/licenses/LICENSE-2.0"))), Nil,
        Some(Versions.DateTime(2019, 10, 16, 12, 26, 29)), None))

      assert(result == expected)
    }

    'infoWithoutHomePage {
      val node = """
        <ivy-module version="2.0" xmlns:e="http://ant.apache.org/ivy/extra">
          <info organisation="com.github.gseitz" module="sbt-release" revision="1.0.12" status="release" e:sbtVersion="1.0" e:scalaVersion="2.12">
            <description>
            sbt-release
            </description>
          </info>
        </ivy-module>
      """

      val result = IvyXml.project(xmlParseDom(node).right.get).map(_.info)
      val expected = Right(Info("sbt-release", "", Nil, Nil, None, None))

      assert(result == expected)
    }
  }
}
