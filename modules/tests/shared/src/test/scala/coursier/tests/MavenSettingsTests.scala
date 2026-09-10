package coursier.tests

import coursier.maven.MavenSettings
import utest._

object MavenSettingsTests extends TestSuite {

  val tests = Tests {
    test("mirrors") {
      val content =
        """<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0">
          |  <mirrors>
          |    <mirror>
          |      <id>internal</id>
          |      <name>Internal mirror</name>
          |      <url>https://nexus.example.com/repository/maven-public/</url>
          |      <mirrorOf>external:*,!snapshots</mirrorOf>
          |    </mirror>
          |    <mirror>
          |      <id>blocker</id>
          |      <url>http://0.0.0.0/</url>
          |      <mirrorOf>external:http:*</mirrorOf>
          |      <blocked>true</blocked>
          |      <mirrorOfLayouts>default,legacy</mirrorOfLayouts>
          |    </mirror>
          |  </mirrors>
          |</settings>
          |""".stripMargin

      val expected = Right(
        MavenSettings(
          Seq(
            MavenSettings.Mirror(
              "internal",
              "https://nexus.example.com/repository/maven-public/",
              "external:*,!snapshots"
            ),
            MavenSettings.Mirror(
              "blocker",
              "http://0.0.0.0/",
              "external:http:*",
              "default,legacy",
              true
            )
          ),
          Nil
        )
      )

      val result = MavenSettings.parse(content)

      assert(result == expected)
    }

    test("servers") {
      val content =
        """<settings>
          |  <servers>
          |    <server>
          |      <id>internal</id>
          |      <username>alex</username>
          |      <password>1234</password>
          |    </server>
          |    <server>
          |      <id>key-based</id>
          |      <privateKey>${user.home}/.ssh/id_dsa</privateKey>
          |    </server>
          |  </servers>
          |</settings>
          |""".stripMargin

      val expected = Right(
        MavenSettings(
          Nil,
          Seq(
            MavenSettings.Server("internal", Some("alex"), Some("1234")),
            MavenSettings.Server("key-based")
          )
        )
      )

      val result = MavenSettings.parse(content)

      assert(result == expected)
    }

    test("emptySettings") {
      val result = MavenSettings.parse("<settings/>")
      assert(result == Right(MavenSettings()))
    }

    test("mirrorWithNoUrl") {
      val content =
        """<settings>
          |  <mirrors>
          |    <mirror>
          |      <id>internal</id>
          |      <mirrorOf>*</mirrorOf>
          |    </mirror>
          |  </mirrors>
          |</settings>
          |""".stripMargin

      val result = MavenSettings.parse(content)

      assert(result == Left("No url found in mirror 'internal'"))
    }

    test("notASettingsFile") {
      val result = MavenSettings.parse("<project/>")
      assert(result == Left("Expected a settings element at the root, got 'project'"))
    }
  }
}
