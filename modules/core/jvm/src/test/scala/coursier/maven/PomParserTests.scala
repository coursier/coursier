package coursier.maven

import utest._
import coursier.core.Info

object PomParserTests extends TestSuite {

  val tests = Tests {
    /** Verifies the `scm field is optional` scenario behaves as the user expects. */
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

    /** Verifies the `all fields in scm is optional` scenario behaves as the user expects. */
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

    /** Verifies the `can parse scm info` scenario behaves as the user expects. */
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

    /** Verifies the `properties are parsed` scenario behaves as the user expects. */
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

    /** Verifies the `licenses are optional` scenario behaves as the user expects. */
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

    /** Verifies the `licenses with just name and url` scenario behaves as the user expects. */
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

    /** Verifies the `licenses with just name and url (binary compat test)` scenario behaves as the user expects. */
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

    /** Verifies the `multiple licenses with just name and url` scenario behaves as the user expects. */
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

    /** Verifies the `license with maven distribution and comments` scenario behaves as the user expects. */
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

    /** Verifies the `license with maven distribution and comments (binary compat test)` scenario behaves as the user expects. */
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

    /** Verifies the `'/' and '\' are invalid in groupId` scenario behaves as the user expects. */
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

    /** Verifies the `'/' and '\' are invalid in artifactId` scenario behaves as the user expects. */
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

    /** Verifies the `'/' and '\' are invalid in version` scenario behaves as the user expects. */
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
  }
}
