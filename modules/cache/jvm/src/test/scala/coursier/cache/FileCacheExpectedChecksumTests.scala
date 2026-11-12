package coursier.cache

import java.security.MessageDigest

import utest._

import coursier.cache.TestUtil._
import coursier.util.{Artifact, Task}

object FileCacheExpectedChecksumTests extends TestSuite {

  private def sha256(content: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(content).map(b => f"$b%02x").mkString

  private def withSource[T](f: (os.Path, Artifact, String) => T): T =
    withTmpDir { dir =>
      val source  = dir / "source.txt"
      val content = "hello".getBytes("UTF-8")
      os.write(source, content)
      f(dir, Artifact(source.toNIO.toUri.toASCIIString), sha256(content))
    }

  private def fileWithChecksum(
    cache: FileCache[Task],
    artifact: Artifact,
    checksum: ExpectedChecksum
  ): Either[ArtifactError, os.Path] =
    cache.fileWithChecks(artifact, integrityCheck = false, expectedChecksum = Some(checksum))
      .run
      .unsafeRun()(cache.ec)
      .map(os.Path(_))

  val tests = Tests {

    test("expected checksum") {
      withSource { (dir, artifact, sha256) =>
        val cache = FileCache[Task]((dir / "cache").toIO).copy(localArtifactsShouldBeCached = true)
        val res   = fileWithChecksum(cache, artifact, ExpectedChecksum.sha256(sha256.toUpperCase))
        val file  = res.fold(err => throw new Exception(err), identity)
        assert(file.startsWith(dir / "cache"))
        // the digest is persisted
        assert(os.exists(file / os.up / s".${file.last}__sha256.computed"))
      }
    }

    test("wrong checksum") {
      withSource { (dir, artifact, sha256) =>
        val cache = FileCache[Task]((dir / "cache").toIO).copy(localArtifactsShouldBeCached = true)
        val wrong = "ab" * 32
        val res   = fileWithChecksum(cache, artifact, ExpectedChecksum.sha256(wrong))
        res match {
          case Left(err: ArtifactError.UnexpectedChecksum) =>
            assert(err.expected == wrong)
            assert(err.got == sha256)
          case other =>
            throw new Exception(s"Unexpected result: $other")
        }
      }
    }

    test("wrong checksum of file used in place") {
      withSource { (dir, artifact, _) =>
        val cache = FileCache[Task]((dir / "cache").toIO)
        val res   = fileWithChecksum(cache, artifact, ExpectedChecksum.sha256("ab" * 32))
        val isUnexpectedChecksum = res match {
          case Left(_: ArtifactError.UnexpectedChecksum) => true
          case _                                         => false
        }
        assert(isUnexpectedChecksum)
        // not deleted, and nothing written next to it
        assert(os.list(dir).map(_.last).toSet == Set("source.txt"))
      }
    }

    test("unsupported algorithm") {
      withSource { (dir, artifact, sha256) =>
        val cache = FileCache[Task]((dir / "cache").toIO).copy(localArtifactsShouldBeCached = true)
        val res   = fileWithChecksum(cache, artifact, ExpectedChecksum("FOO-256", sha256))
        assert(res.left.exists(_.getMessage.contains("Unsupported checksum algorithm FOO-256")))
      }
    }
  }
}
