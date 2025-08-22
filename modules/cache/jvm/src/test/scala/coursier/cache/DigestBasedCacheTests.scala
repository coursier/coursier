package coursier.cache

import coursier.cache.TestUtil._
import coursier.core.Authentication
import coursier.util.{Artifact, Task}
import utest._

object DigestBasedCacheTests extends TestSuite {

  val tests = Tests {

    test("simple") {
      withTmpDir { dir =>
        // To force re-download things, use this instead:
        // ArchiveCacheTests.sandboxedCache
        val cache       = FileCache()
        val digestBased = DigestBasedCache[Task]((dir / "digest").toNIO)

        val repoName = "library/hello-world"
        val auth     = Authentication.byNameBearerToken(
          DockerTestUtil.token(repoName)
        )

        val sha256 = "f1f77a0f96b7251d7ef5472705624e2d76db64855b5b121e1cbefe9dc52d0f86"
        val file   = cache.file(
          Artifact(s"https://registry-1.docker.io/v2/$repoName/blobs/sha256:$sha256")
            .withAuthentication(Some(auth))
        )
          .run.unsafeRun(wrapExceptions = true)(cache.ec)
          .toTry.get

        val digestCachedFile     = digestBased.`import`(DigestArtifact(sha256, file.toPath))
        val digestCachedFile0Opt = digestBased.get(sha256)

        assert(digestCachedFile0Opt.contains(digestCachedFile))
      }
    }

  }

}
