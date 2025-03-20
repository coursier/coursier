package coursier.cache

import coursier.cache.TestUtil._
import coursier.core.Authentication
import coursier.util.Artifact
import utest._

import java.nio.file.Files

object DigestBasedArchiveCacheTests extends TestSuite {

  val tests = Tests {

    test("simple") {
      withTmpDir { tmpDir =>
        val archiveCache0 = ArchiveCacheTests.archiveCache(tmpDir / "arc")
        val digestBased   = DigestBasedArchiveCache(archiveCache0)
        val cache         = archiveCache0.cache

        val repoName = "library/hello-world"
        val auth = Authentication.byNameBearerToken(
          DockerTestUtil.token(repoName)
        )

        val sha256 = "c9c5fd25a1bdc181cb012bc4fbb1ab272a975728f54064b7ae3ee8e77fd28c46"
        val file = cache.file(
          Artifact(s"https://registry-1.docker.io/v2/$repoName/blobs/sha256:$sha256")
            .withAuthentication(Some(auth))
        )
          .run.unsafeRun()(cache.ec)
          .toTry.get
          .toPath

        val dir = digestBased.get(DigestArtifact(sha256, file))
          .unsafeRun()(cache.ec)
          .toTry.get
          .toPath

        assert(Files.exists(dir.resolve("hello")))
        assert(Files.isRegularFile(dir.resolve("hello")))
      }
    }

  }

}
