package coursier.tests

import coursier.core.{Profile => _, Project => _, _}
import coursier.core.compatibility._
import coursier.util.Traverse.TraverseOps
import coursier.maven.Pom
import coursier.tests.TestUtil._
import coursier.util.StringInterpolators._
import utest._

object PomParsingTests extends TestSuite {

  val tests = Tests {
    test("readClassifier") {
      val depNode = """
        <dependency>
          <groupId>comp</groupId>
          <artifactId>lib</artifactId>
          <version>2.1</version>
          <classifier>extra</classifier>
        </dependency>
                   """

      val expected = Right(
        Configuration.empty -> dep"comp:lib:2.1".withAttributes(
          Attributes(Type.empty, Classifier("extra"))
        )
      )

      val result = Pom.dependency(xmlParseDom(depNode).toOption.get)

      assert(result == expected)
    }
    test("readProfileWithNoActivation") {
      val profileNode = """
        <profile>
          <id>profile1</id>
        </profile>
                       """

      val expected = Right(Profile("profile1", None, Profile.Activation(Nil), Nil, Nil, Map.empty))

      val result = Pom.profile(xmlParseDom(profileNode).toOption.get)

      assert(result == expected)
    }
    test("beFineWithProfilesWithNoId") {
      val profileNode = """
        <profile>
          <activation>
            <activeByDefault>true</activeByDefault>
          </activation>
        </profile>
                       """

      val expected = Right(Profile("", Some(true), Profile.Activation(Nil), Nil, Nil, Map.empty))

      val result = Pom.profile(xmlParseDom(profileNode).toOption.get)

      assert(result == expected)
    }
    test("readProfileActivatedByDefault") {
      val profileNode = """
        <profile>
          <id>profile1</id>
          <activation>
            <activeByDefault>true</activeByDefault>
          </activation>
        </profile>
                       """

      val expected =
        Right(Profile("profile1", Some(true), Profile.Activation(Nil), Nil, Nil, Map.empty))

      val result = Pom.profile(xmlParseDom(profileNode).toOption.get)

      assert(result == expected)
    }
    test("readProfileActiveByPropertyWithoutValue") {
      val profileNode = """
        <profile>
          <id>profile1</id>
          <activation>
            <property>
              <name>hadoop.profile</name>
            </property>
          </activation>
        </profile>
                       """
      val expected = Right {
        Profile(
          "profile1",
          None,
          Profile.Activation(List("hadoop.profile" -> None)),
          Nil,
          Nil,
          Map.empty
        )
      }
      val result = Pom.profile(xmlParseDom(profileNode).toOption.get)

      assert(result == expected)
    }

    test("readProfileActiveByPropertyWithValue") {
      val profileNode = """
        <profile>
          <id>profile1</id>
          <activation>
            <property>
              <name>hadoop.profile</name>
              <value>yes</value>
            </property>
          </activation>
        </profile>
                       """
      val expected = Right {
        Profile(
          "profile1",
          None,
          Profile.Activation(List("hadoop.profile" -> Some("yes"))),
          Nil,
          Nil,
          Map.empty
        )
      }
      val result = Pom.profile(xmlParseDom(profileNode).toOption.get)

      assert(result == expected)
    }

    test("readProfileDependencies") {
      val profileNode = """
        <profile>
          <id>profile1</id>
          <dependencies>
            <dependency>
              <groupId>comp</groupId>
              <artifactId>lib</artifactId>
              <version>0.2</version>
            </dependency>
          </dependencies>
        </profile>
                       """

      val expected = Right {
        Profile(
          "profile1",
          None,
          Profile.Activation(Nil),
          Seq(
            Configuration.empty -> dep"comp:lib:0.2"
          ),
          Nil,
          Map.empty
        )
      }

      val result = Pom.profile(xmlParseDom(profileNode).toOption.get)

      assert(result == expected)
    }
    test("readProfileDependenciesMgmt") {
      val profileNode = """
        <profile>
          <id>profile1</id>
          <dependencyManagement>
            <dependencies>
              <dependency>
                <groupId>comp</groupId>
                <artifactId>lib</artifactId>
                <version>0.2</version>
                <scope>test</scope>
              </dependency>
            </dependencies>
          </dependencyManagement>
        </profile>
                       """

      val expected = Right {
        Profile(
          "profile1",
          None,
          Profile.Activation(Nil),
          Nil,
          Seq(
            Configuration.test -> dep"comp:lib:0.2"
          ),
          Map.empty
        )
      }

      val result = Pom.profile(xmlParseDom(profileNode).toOption.get)

      assert(result == expected)
    }
    test("readProfileProperties") {
      val profileNode = """
        <profile>
          <id>profile1</id>
          <properties>
            <first.prop>value1</first.prop>
          </properties>
        </profile>
                       """

      val expected = Right {
        Profile(
          "profile1",
          None,
          Profile.Activation(Nil),
          Nil,
          Nil,
          Map("first.prop" -> "value1")
        )
      }

      val result = Pom.profile(xmlParseDom(profileNode).toOption.get)

      assert(result == expected)
    }
    test("propertyWithSpaces") {
      val profileNode = """
        <profile>
          <id>profile1</id>
          <properties>
            <first.prop>   value1   </first.prop>
          </properties>
        </profile>
                       """

      val expected = Right {
        Profile(
          "profile1",
          None,
          Profile.Activation(Nil),
          Nil,
          Nil,
          Map("first.prop" -> "value1")
        )
      }

      val result = Pom.profile(xmlParseDom(profileNode).toOption.get)

      assert(result == expected)
    }
    test("beFineWithCommentsInProperties") {

      val properties =
        """
          |  <properties>
          |    <maven.compile.source>1.6</maven.compile.source>
          |    <maven.compile.target>1.6</maven.compile.target>
          |    <commons.componentid>io</commons.componentid>
          |    <commons.rc.version>RC1</commons.rc.version>
          |    <commons.release.version>2.4</commons.release.version>
          |    <commons.release.desc>(requires JDK 1.6+)</commons.release.desc>
          |    <commons.release.2.version>2.2</commons.release.2.version>
          |    <commons.release.2.desc>(requires JDK 1.5+)</commons.release.2.desc>
          |    <commons.jira.id>IO</commons.jira.id>
          |    <commons.jira.pid>12310477</commons.jira.pid>
          |    <commons.osgi.export>
          |        <!-- Explicit list of packages from IO 1.4 -->
          |        org.apache.commons.io;
          |        org.apache.commons.io.comparator;
          |        org.apache.commons.io.filefilter;
          |        org.apache.commons.io.input;
          |        org.apache.commons.io.output;version=1.4.9999;-noimport:=true,
          |        <!-- Same list plus * for new packages -->
          |        org.apache.commons.io;
          |        org.apache.commons.io.comparator;
          |        org.apache.commons.io.filefilter;
          |        org.apache.commons.io.input;
          |        org.apache.commons.io.output;
          |        org.apache.commons.io.*;version=${project.version};-noimport:=true
          |    </commons.osgi.export>
          |  </properties>
          |
        """.stripMargin

      val parsed = xmlParseDom(properties)
      assert(parsed.isRight)

      val node = parsed.toOption.get
      assert(node.label == "properties")

      val children = node.children.collect { case elem if elem.isElement => elem }
      val props0   = children.eitherTraverse(Pom.property)

      assert(props0.isRight)

      val props = props0.getOrElse(???).toMap

      assert(props.contains("commons.release.2.desc"))
      assert(props.contains("commons.osgi.export"))

      assert(props("commons.rc.version") == "RC1")
      assert(props("commons.release.2.desc") == "(requires JDK 1.5+)")
      assert(props("commons.osgi.export").contains("org.apache.commons.io.filefilter;"))
      assert(props("commons.osgi.export").contains("org.apache.commons.io.input;"))
    }

    test("projectWithScm") {
      val profileNode =
        """
          |<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          |    <modelVersion>4.0.0</modelVersion>
          |    <groupId>com.example</groupId>
          |    <artifactId>awesome-project</artifactId>
          |    <version>1.0-SNAPSHOT</version>
          |
          |    <url>https://example.com</url>
          |    <scm>
          |      <url>https://github.com/coursier/coursier</url>
          |      <connection>scm:git:git@github.com:coursier/coursier.git</connection>
          |    </scm>
          |</project>""".stripMargin

      val expected = Right(Project(mod"com.example:awesome-project", "1.0-SNAPSHOT").withInfo(Info(
        description = "",
        homePage = "https://example.com",
        licenses = Seq.empty,
        developers = Seq.empty,
        publication = None,
        scm = Some(Info.Scm(
          url = Some("https://github.com/coursier/coursier"),
          connection = Some("scm:git:git@github.com:coursier/coursier.git"),
          developerConnection = None
        ))
      )))

      val result = Pom.project(xmlParseDom(profileNode).toOption.get)

      assert(result == expected)
    }

    test("caseInsensitiveOperatingSystemActivation") {
      val profileNode = """
        <profile>
          <id>profile1</id>
          <activation>
            <os>
              <family>Windows</family>
              <arch>amd64</arch>
            </os>
          </activation>
        </profile>
                       """
      val profile     = Pom.profile(xmlParseDom(profileNode).toOption.get).toOption.get
      val windowsOs = Activation.Os.fromProperties(Map(
        "os.name"        -> "Windows 10",
        "os.arch"        -> "amd64",
        "os.version"     -> "10.0",
        "path.separator" -> ";"
      ))
      val isActive = profile.activation.os.isActive(windowsOs)

      assert(isActive)
    }
  }

}
