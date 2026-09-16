package coursier.maven

import utest._

import coursier.core.{Info, Module, ModuleName, Organization, Project}
import coursier.version.{Latest, VersionConstraint}

object PomParserTests extends TestSuite {

  val tests = Tests {
    test("scm field is optional") {
      val success = MavenRepository.parseRawPomSax(
        """
          |<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          |    <modelVersion>4.0.0</modelVersion>
          |    <groupId>com.example</groupId>
          |    <artifactId>awesome-project</artifactId>
          |    <version>1.0-SNAPSHOT</version>
          |</project>""".stripMargin
      )
      assert(success.isRight)
      val scm = success.toOption.get.info.scm
      assert(scm.isEmpty)
    }

    test("all fields in scm is optional") {
      val success = MavenRepository.parseRawPomSax(
        """
          |<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          |    <modelVersion>4.0.0</modelVersion>
          |    <groupId>com.example</groupId>
          |    <artifactId>awesome-project</artifactId>
          |    <version>1.0-SNAPSHOT</version>
          |
          |    <scm>
          |    </scm>
          |</project>""".stripMargin
      )
      assert(success.isRight)
      val scm = success.toOption.get.info.scm
      assert(scm.exists(_.url.isEmpty))
      assert(scm.exists(_.connection.isEmpty))
      assert(scm.exists(_.developerConnection.isEmpty))
    }

    test("can parse scm info") {
      val success = MavenRepository.parseRawPomSax(
        """
          |<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          |    <modelVersion>4.0.0</modelVersion>
          |    <groupId>com.example</groupId>
          |    <artifactId>awesome-project</artifactId>
          |    <version>1.0-SNAPSHOT</version>
          |
          |    <scm>
          |      <url>https://github.com/coursier/coursier</url>
          |      <connection>scm:git:git@github.com:coursier/coursier.git</connection>
          |      <developerConnection>foo</developerConnection>
          |    </scm>
          |</project>""".stripMargin
      )
      assert(success.isRight)
      val scm = success.toOption.get.info.scm
      assert(scm.exists(_.url.contains("https://github.com/coursier/coursier")))
      assert(scm.exists(_.connection.contains("scm:git:git@github.com:coursier/coursier.git")))
      assert(scm.exists(_.developerConnection.contains("foo")))
    }

    test("properties are parsed") {
      val success = MavenRepository.parseRawPomSax(
        """
          |<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          |    <modelVersion>4.0.0</modelVersion>
          |    <groupId>com.example</groupId>
          |    <artifactId>awesome-project</artifactId>
          |    <version>1.0-SNAPSHOT</version>
          |
          |    <properties>
          |        <info.versionScheme>semver-spec</info.versionScheme>
          |    </properties>
          |</project>""".stripMargin
      )
      assert(success.isRight)
      val properties = success.toOption.get.properties
      val expected   = Seq("info.versionScheme" -> "semver-spec")
      assert(properties == expected)
    }

    test("licenses are optional") {
      val success = MavenRepository.parseRawPomSax(
        """
          |<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          |  <modelVersion>4.0.0</modelVersion>
          |  <groupId>com.example</groupId>
          |  <artifactId>awesome-project</artifactId>
          |  <version>1.0-SNAPSHOT</version>
          |</project>""".stripMargin
      )
      assert(success.isRight)
      val licenseInfo = success.toOption.get.info.licenseInfo
      val expected    = Seq()
      assert(licenseInfo == expected)
    }

    test("licenses with just name and url") {
      val success = MavenRepository.parseRawPomSax(
        """
          |<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          |  <modelVersion>4.0.0</modelVersion>
          |  <groupId>com.example</groupId>
          |  <artifactId>awesome-project</artifactId>
          |  <version>1.0-SNAPSHOT</version>
          |  <licenses>
          |    <license>
          |      <name>Apache License, Version 2.0</name>
          |      <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
          |    </license>
          |  </licenses>
          |</project>""".stripMargin
      )
      assert(success.isRight)
      val licenseInfo = success.toOption.get.info.licenseInfo
      val expected = Seq(
        Info.License(
          "Apache License, Version 2.0",
          Some("https://www.apache.org/licenses/LICENSE-2.0.txt"),
          None,
          None
        )
      )
      assert(licenseInfo == expected)
    }

    test("licenses with just name and url (binary compat test)") {
      val success = MavenRepository.parseRawPomSax(
        """
          |<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          |  <modelVersion>4.0.0</modelVersion>
          |  <groupId>com.example</groupId>
          |  <artifactId>awesome-project</artifactId>
          |  <version>1.0-SNAPSHOT</version>
          |  <licenses>
          |    <license>
          |      <name>Apache License, Version 2.0</name>
          |      <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
          |    </license>
          |  </licenses>
          |</project>""".stripMargin
      )
      assert(success.isRight)
      val licenses = success.toOption.get.info.licenses
      val expected = Seq(
        "Apache License, Version 2.0" -> Some("https://www.apache.org/licenses/LICENSE-2.0.txt")
      )
      assert(licenses == expected)
    }

    test("multiple licenses with just name and url") {
      val success = MavenRepository.parseRawPomSax(
        """
          |<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          |  <modelVersion>4.0.0</modelVersion>
          |  <groupId>com.example</groupId>
          |  <artifactId>awesome-project</artifactId>
          |  <version>1.0-SNAPSHOT</version>
          |  <licenses>
          |    <license>
          |      <name>Apache License, Version 2.0</name>
          |      <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
          |    </license>
          |    <license>
          |      <name>Fake Awesome License 3.0</name>
          |      <url>https://fake-awesome-license.org</url>
          |    </license>
          |  </licenses>
          |</project>""".stripMargin
      )
      assert(success.isRight)
      val licenseInfo = success.toOption.get.info.licenseInfo
      val expected = Seq(
        Info.License(
          "Apache License, Version 2.0",
          Some("https://www.apache.org/licenses/LICENSE-2.0.txt"),
          None,
          None
        ),
        Info.License(
          "Fake Awesome License 3.0",
          Some("https://fake-awesome-license.org"),
          None,
          None
        )
      )
      assert(licenseInfo == expected)
    }

    test("license with maven distribution and comments") {
      val success = MavenRepository.parseRawPomSax(
        """
          |<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          |  <modelVersion>4.0.0</modelVersion>
          |  <groupId>com.example</groupId>
          |  <artifactId>awesome-project</artifactId>
          |  <version>1.0-SNAPSHOT</version>
          |  <licenses>
          |    <license>
          |      <name>Apache License, Version 2.0</name>
          |      <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
          |      <distribution>repo</distribution>
          |      <comments>Very insightful comment</comments>
          |    </license>
          |  </licenses>
          |</project>""".stripMargin
      )
      assert(success.isRight)
      val licenseInfo = success.toOption.get.info.licenseInfo
      val expected = Seq(
        Info.License(
          "Apache License, Version 2.0",
          Some("https://www.apache.org/licenses/LICENSE-2.0.txt"),
          Some("repo"),
          Some("Very insightful comment")
        )
      )
      assert(licenseInfo == expected)
    }

    test("license with maven distribution and comments (binary compat test)") {
      val success = MavenRepository.parseRawPomSax(
        """
          |<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          |  <modelVersion>4.0.0</modelVersion>
          |  <groupId>com.example</groupId>
          |  <artifactId>awesome-project</artifactId>
          |  <version>1.0-SNAPSHOT</version>
          |  <licenses>
          |    <license>
          |      <name>Apache License, Version 2.0</name>
          |      <url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>
          |      <distribution>repo</distribution>
          |      <comments>Very insightful comment</comments>
          |    </license>
          |  </licenses>
          |</project>""".stripMargin
      )
      assert(success.isRight)
      val licenses = success.toOption.get.info.licenses
      val expected = Seq(
        "Apache License, Version 2.0" -> Some("https://www.apache.org/licenses/LICENSE-2.0.txt")
      )
      assert(licenses == expected)
    }

    test("'/' and '\\' are invalid in groupId") {
      val failure = MavenRepository.parseRawPomSax(
        """
          |<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          |    <modelVersion>4.0.0</modelVersion>
          |    <groupId>com/example</groupId>
          |    <artifactId>awesome.project</artifactId>
          |    <version>1.0-SNAPSHOT</version>
          |</project>""".stripMargin
      )
      assert(failure.isLeft)
      val message = failure.left.toOption.get
      assert(message.contains("com/example"))
    }

    test("'/' and '\\' are invalid in artifactId") {
      val failure = MavenRepository.parseRawPomSax(
        """
          |<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          |    <modelVersion>4.0.0</modelVersion>
          |    <groupId>com.example</groupId>
          |    <artifactId>awesome\project</artifactId>
          |    <version>1.0-SNAPSHOT</version>
          |</project>""".stripMargin
      )
      assert(failure.isLeft)
      val message = failure.left.toOption.get
      assert(message.contains("awesome\\project"))
    }

    test("'/' and '\\' are invalid in version") {
      val failure = MavenRepository.parseRawPomSax(
        """
          |<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
          |    <modelVersion>4.0.0</modelVersion>
          |    <groupId>com.example</groupId>
          |    <artifactId>awesome_project</artifactId>
          |    <version>1.0/SNAPSHOT</version>
          |</project>""".stripMargin
      )
      assert(failure.isLeft)
      val message = failure.left.toOption.get
      assert(message.contains("1.0/SNAPSHOT"))
    }

    test("parent version interval") {
      val pom =
        """
          |<project xmlns="http://maven.apache.org/POM/4.0.0">
          |    <modelVersion>4.0.0</modelVersion>
          |    <parent>
          |        <groupId>com.example</groupId>
          |        <artifactId>parent</artifactId>
          |        <version>[1.0,2.0)</version>
          |    </parent>
          |    <artifactId>child</artifactId>
          |    <version>1.0</version>
          |</project>""".stripMargin

      val expectedParent = Some((
        Module(Organization("com.example"), ModuleName("parent"), Map.empty),
        VersionConstraint("[1.0,2.0)")
      ))

      val sax = MavenRepository.parseRawPomSax(pom)
      val dom = MavenRepository.parseRawPomDom(pom)

      assert(sax.map(_.parent0) == Right(expectedParent))
      assert(dom.map(_.parent0) == Right(expectedParent))
    }

    test("version inherited from a parent with a version interval") {
      // The child's own version cannot be known until the parent is resolved. Parsing must
      // stay non-fatal here - Project.actualVersion0, set from the version the metadata was
      // fetched at, is what's authoritative downstream. Both parsers must agree.
      val pom =
        """
          |<project xmlns="http://maven.apache.org/POM/4.0.0">
          |    <modelVersion>4.0.0</modelVersion>
          |    <parent>
          |        <groupId>com.example</groupId>
          |        <artifactId>parent</artifactId>
          |        <version>[1.0,2.0)</version>
          |    </parent>
          |    <artifactId>child</artifactId>
          |</project>""".stripMargin

      val sax = MavenRepository.parseRawPomSax(pom)
      val dom = MavenRepository.parseRawPomDom(pom)

      assert(sax.isRight)
      assert(dom.isRight)
      assert(sax.map(_.version0.asString) == Right("[1.0,2.0)"))
      assert(dom.map(_.version0.asString) == Right("[1.0,2.0)"))
      assert(sax.map(_.parent0) == dom.map(_.parent0))
    }

    test("maven 2 meta versions") {
      // RELEASE and LATEST stand for the release / latest fields of the module's
      // maven-metadata.xml, which coursier handles as latest.release / latest.integration
      val pom =
        """
          |<project xmlns="http://maven.apache.org/POM/4.0.0">
          |    <modelVersion>4.0.0</modelVersion>
          |    <groupId>com.example</groupId>
          |    <artifactId>child</artifactId>
          |    <version>1.0</version>
          |    <dependencies>
          |        <dependency>
          |            <groupId>com.example</groupId>
          |            <artifactId>lib1</artifactId>
          |            <version>RELEASE</version>
          |        </dependency>
          |        <dependency>
          |            <groupId>com.example</groupId>
          |            <artifactId>lib2</artifactId>
          |            <version>LATEST</version>
          |        </dependency>
          |    </dependencies>
          |    <dependencyManagement>
          |        <dependencies>
          |            <dependency>
          |                <groupId>com.example</groupId>
          |                <artifactId>lib3</artifactId>
          |                <version>RELEASE</version>
          |            </dependency>
          |        </dependencies>
          |    </dependencyManagement>
          |</project>""".stripMargin

      val expectedDeps = Right(Seq(
        "lib1" -> Some(Latest.Release),
        "lib2" -> Some(Latest.Integration)
      ))
      val expectedDepMgmt = Right(Seq("lib3" -> Some(Latest.Release)))

      def deps(proj: Project) =
        proj.dependencies0.map {
          case (_, dep) =>
            dep.module.name.value -> dep.versionConstraint.latest
        }
      def depMgmt(proj: Project) =
        proj.dependencyManagement0.map {
          case (_, dep) =>
            dep.module.name.value -> dep.versionConstraint.latest
        }

      val sax = MavenRepository.parseRawPomSax(pom)
      val dom = MavenRepository.parseRawPomDom(pom)

      assert(sax.map(deps) == expectedDeps)
      assert(dom.map(deps) == expectedDeps)
      assert(sax.map(depMgmt) == expectedDepMgmt)
      assert(dom.map(depMgmt) == expectedDepMgmt)
    }
  }
}
