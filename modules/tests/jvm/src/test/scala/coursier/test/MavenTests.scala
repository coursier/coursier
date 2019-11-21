package coursier.test

import java.io.File

import coursier.{Attributes, Classifier, Dependency, Type, moduleString}
import coursier.maven.MavenRepository
import coursier.test.compatibility.executionContext
import utest._

object MavenTests extends TestSuite {

  // only tested on the JVM for lack of support of XML attributes in the platform-dependent XML stubs

  private val runner = new TestRunner

  val tests = Tests {
    'testSnapshotNoVersioning - {

      val dep = Dependency(mod"com.abc:test-snapshot-special", "0.1.0-SNAPSHOT")
        .withTransitive(false)
        .withAttributes(Attributes())

      val repoBase = new File(HandmadeMetadata.repoBase, "http/abc.com")
        .toURI
        .toASCIIString
        .stripSuffix("/") + "/"
      val repo = MavenRepository(repoBase)

      val mainJarUrl = repoBase + "com/abc/test-snapshot-special/0.1.0-SNAPSHOT/test-snapshot-special-0.1.0-20170421.034426-82.jar"
      val sourcesJarUrl = repoBase + "com/abc/test-snapshot-special/0.1.0-SNAPSHOT/test-snapshot-special-0.1.0-20170421.034426-82-sources.jar"

      * - runner.withArtifacts(
        dep = dep.withAttributes(Attributes(Type.jar)),
        extraRepos = Seq(repo),
        classifierOpt = None
      ) {
        case Seq(artifact) =>
          assert(artifact.url == mainJarUrl)
        case other =>
          throw new Exception(s"Unexpected number of artifacts\n${other.mkString("\n")}")
      }

      * - runner.withArtifacts(
        dep = dep.withAttributes(Attributes(Type.source)),
        extraRepos = Seq(repo),
        classifierOpt = Some(Classifier.sources)
      ) {
        case Seq(artifact) =>
          assert(artifact.url == sourcesJarUrl)
        case other =>
          throw new Exception(s"Unexpected number of artifacts\n${other.mkString("\n")}")
      }
    }
  }
}
