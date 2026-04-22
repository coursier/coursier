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
    test("normalizesTrailingSlashInToWithSeqCtor") {
      val mirror   = MavenMirror(Seq("*"), "https://proxy.example.com/")
      val replaced = mirror.matches(MavenRepository("https://repo1.maven.org/maven2")).get
      assert(replaced.asInstanceOf[MavenRepository].root == "https://proxy.example.com")
    }

    test("preservesSbtMavenRepositoryAfterMirroring") {
      val mirror = MavenMirror(Seq("*"), "https://proxy.example.com/")
      val sbtRepo = SbtMavenRepository("https://repo1.maven.org/maven2")

      val replaced = mirror.matches(sbtRepo).get

      assert(replaced.isInstanceOf[SbtMavenRepository])
      assert(replaced.asInstanceOf[SbtMavenRepository].root == "https://proxy.example.com")
      assert(replaced.asInstanceOf[SbtMavenRepository].moduleDirectory(sbtPluginModule) ==
        "sbt-mima-plugin_2.12_1.0")
    }
  }
}
