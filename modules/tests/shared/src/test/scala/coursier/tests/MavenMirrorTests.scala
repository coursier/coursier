package coursier.tests

import coursier.core.{Module, ModuleName, Organization}
import coursier.maven.{MavenRepository, SbtMavenRepository}
import coursier.params.MavenMirror
import utest._

object MavenMirrorTests extends TestSuite {

  private val sbtPluginModule = Module(
    Organization("com.typesafe"),
    ModuleName("sbt-mima-plugin"),
    Map(
      "scalaVersion" -> "2.12",
      "sbtVersion"   -> "1.0"
    )
  )

  val tests = Tests {
    test("trailingSlashInMirrorToUrlIsNormalized") {
      val mirror   = MavenMirror(Seq("*"), "https://proxy.example.com/")
      val replaced = mirror.matches(MavenRepository("https://repo1.maven.org/maven2")).get
      assert(replaced.asInstanceOf[MavenRepository].root == "https://proxy.example.com")
      assert(
        replaced
          .asInstanceOf[MavenRepository]
          .urlFor(Seq("com", "typesafe", "sbt-mima-plugin")) == "https://proxy.example.com/com/typesafe/sbt-mima-plugin"
      )
    }

    test("preservesSbtMavenRepositoryAfterMirroring") {
      val mirror = MavenMirror(Seq("*"), "https://proxy.example.com/")
      val sbtRepo = SbtMavenRepository("https://repo1.maven.org/maven2")

      val replaced = mirror.matches(sbtRepo).get
      val replacedSbt = replaced.asInstanceOf[SbtMavenRepository]
      val moduleDir   = replacedSbt.moduleDirectory(sbtPluginModule)

      assert(replaced.isInstanceOf[SbtMavenRepository])
      assert(replacedSbt.root == "https://proxy.example.com")
      assert(moduleDir == "sbt-mima-plugin_2.12_1.0")
      assert(
        replacedSbt.urlFor(
          Seq(
            "com",
            "typesafe",
            moduleDir,
            "1.1.4",
            s"$moduleDir-1.1.4.pom"
          )
        ) == "https://proxy.example.com/com/typesafe/sbt-mima-plugin_2.12_1.0/1.1.4/sbt-mima-plugin_2.12_1.0-1.1.4.pom"
      )
    }
  }
}
