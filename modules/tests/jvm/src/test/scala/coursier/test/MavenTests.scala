package coursier.test

import java.io.File

import coursier.{Attributes, Dependency, Module, organizationString}
import coursier.maven.MavenRepository
import utest._

object MavenTests extends TestSuite {

  // only tested on the JVM for lack of support of XML attributes in the platform-dependent XML stubs

  val tests = Tests {
    'testSnapshotNoVersioning - {

      val dep = Dependency(
        Module(org"com.abc", "test-snapshot-special"),
        "0.1.0-SNAPSHOT",
        transitive = false,
        attributes = Attributes()
      )

      val repoBase = new File(HandmadeMetadata.repoBase, "http/abc.com")
        .toURI
        .toASCIIString
        .stripSuffix("/") + "/"
      val repo = MavenRepository(repoBase)

      val mainJarUrl = repoBase + "com/abc/test-snapshot-special/0.1.0-SNAPSHOT/test-snapshot-special-0.1.0-20170421.034426-82.jar"
      val sourcesJarUrl = repoBase + "com/abc/test-snapshot-special/0.1.0-SNAPSHOT/test-snapshot-special-0.1.0-20170421.034426-82-sources.jar"

      * - CentralTests.withArtifacts(
        dep = dep.copy(attributes = Attributes("jar")),
        extraRepos = Seq(repo),
        classifierOpt = None
      ) {
        case Seq(artifact) =>
          assert(artifact.url == mainJarUrl)
        case other =>
          throw new Exception(s"Unexpected number of artifacts\n${other.mkString("\n")}")
      }

      * - CentralTests.withArtifacts(
        dep = dep.copy(attributes = Attributes("src")),
        extraRepos = Seq(repo),
        classifierOpt = Some("sources")
      ) {
        case Seq(artifact) =>
          assert(artifact.url == sourcesJarUrl)
        case other =>
          throw new Exception(s"Unexpected number of artifacts\n${other.mkString("\n")}")
      }
    }
  }
}
