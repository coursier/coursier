package coursier

import coursier.core.{Classifier, Configuration}
import coursier.core.compatibility._
import coursier.util.Traverse.TraverseOps
import coursier.maven.MavenRepository
import coursier.maven.Pom
import utest._
import coursier.core.Info
import coursier.core.Info.License

object PomParserTests extends TestSuite {

  val tests = Tests {
    "scm filed is optional" - {
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

    "all fields in scm is optional" - {
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

    "can parse scm info" - {
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

    "properties are parsed" - {
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
      val expected = Seq("info.versionScheme" -> "semver-spec")
      assert(properties == expected)
    }

    "licenses are optional" - {
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
      val expected = Seq()
      assert(licenseInfo == expected)
    }

    "licenses with just name and url" - {
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

    "licenses with just name and url (binary compat test)" - {
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

    "multiple licenses with just name and url" - {
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

    "license with maven distribution and comments" - {
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

    "license with maven distribution and comments (binary compat test)" - {
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
  }
}
