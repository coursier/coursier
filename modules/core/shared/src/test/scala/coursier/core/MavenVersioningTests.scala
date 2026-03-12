package coursier.core

import coursier.version.{Version => Version0}
import coursier.maven.{MavenRepositoryInternal, Pom}
import utest._

object MavenVersioningTests extends TestSuite {

  // from https://github.com/coursier/coursier/issues/3416
  private def testMetadata =
    """<metadata modelVersion="">
      |    <groupId>com.example</groupId>
      |    <artifactId>artifact</artifactId>
      |    <version>2.5.x-SNAPSHOT</version>
      |    <versioning>
      |        <snapshot>
      |            <timestamp>20250522.153927</timestamp>
      |            <buildNumber>2</buildNumber>
      |        </snapshot>
      |        <lastUpdated>20250521153923</lastUpdated>
      |        <snapshotVersions>
      |            <snapshotVersion>
      |                <extension>jar</extension>
      |                <value>2.5.x-20250521.153917-1</value>
      |                <updated>20250521153920</updated>
      |            </snapshotVersion>
      |            <snapshotVersion>
      |                <extension>jar.sha1</extension>
      |                <value>2.5.x-20250521.153917-1</value>
      |                <updated>20250521153921</updated>
      |            </snapshotVersion>
      |            <snapshotVersion>
      |                <extension>jar.md5</extension>
      |                <value>2.5.x-20250521.153917-1</value>
      |                <updated>20250521153923</updated>
      |            </snapshotVersion>
      |            <snapshotVersion>
      |                <extension>jar</extension>
      |                <value>2.5.x-20250522.153927-2</value>
      |                <updated>20250522153924</updated>
      |            </snapshotVersion>
      |            <snapshotVersion>
      |                <extension>jar.sha1</extension>
      |                <value>2.5.x-20250522.153927-2</value>
      |                <updated>20250522153926</updated>
      |            </snapshotVersion>
      |            <snapshotVersion>
      |                <extension>jar.md5</extension>
      |                <value>2.5.x-20250522.153927-2</value>
      |                <updated>20250522153927</updated>
      |            </snapshotVersion>
      |        </snapshotVersions>
      |    </versioning>
      |</metadata>
      |""".stripMargin

  val tests = Tests {
    test("check") {
      val snapshotVersioning = compatibility.xmlParseDom(testMetadata)
        .flatMap(Pom.snapshotVersioning)
        .left.map(err => sys.error(s"Error parsing test data (should not happen): $err"))
        .merge
      val versionOpt = MavenRepositoryInternal.mavenVersioning(
        snapshotVersioning,
        Classifier.empty,
        Extension.jar
      )
      val expected = Version0("2.5.x-20250522.153927-2")
      assert(versionOpt.contains(expected))
    }
  }
}
