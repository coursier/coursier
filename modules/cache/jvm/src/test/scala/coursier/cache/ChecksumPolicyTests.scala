package coursier.cache

import cats.effect.IO
import coursier.cache.TestUtil._
import coursier.util.{Artifact, EnvValues, Task}
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import utest._

import java.io.File
import java.security.MessageDigest

/** Which checksum types are checked, and whether a missing checksum is accepted
  *
  * The types are configured with `FileCache#checksums`, whose default comes from
  * `COURSIER_CHECKSUMS` / `coursier.checksums`, see `CacheEnv.parseChecksums`.
  */
object ChecksumPolicyTests extends TestSuite {

  private val content = "the artifact content"

  private def sha256(b: Array[Byte]): String = {
    val md = MessageDigest.getInstance("SHA-256")
    new java.math.BigInteger(1, md.digest(b)).toString(16)
  }

  private val goodSha1   = sha1(content.getBytes("UTF-8"))
  private val goodSha256 = sha256(content.getBytes("UTF-8"))
  private val badSha256  = sha256("something else entirely".getBytes("UTF-8"))

  /** Serves `/dir/foo.jar`, plus its SHA-1 and SHA-256 files if passed */
  private def withServer[T](
    sha1Opt: Option[String],
    sha256Opt: Option[String]
  )(f: Artifact => T): T = {
    val routes = HttpRoutes.of[IO] {
      case GET -> Root / "dir" / "foo.jar"                              => Ok(content)
      case GET -> Root / "dir" / "foo.jar.sha1" if sha1Opt.nonEmpty     => Ok(sha1Opt.mkString)
      case GET -> Root / "dir" / "foo.jar.sha256" if sha256Opt.nonEmpty => Ok(sha256Opt.mkString)
    }
    withHttpServer(routes) { serverUri =>
      val url = (serverUri / "dir" / "foo.jar").renderString
      // the checksum URLs `withDefaultChecksums` adds for Maven and Ivy repositories
      f(Artifact(url).copy(checksumUrls =
        Map("MD5" -> s"$url.md5", "SHA-1" -> s"$url.sha1", "SHA-256" -> s"$url.sha256")
      ))
    }
  }

  private def run(
    artifact: Artifact,
    checksums: Seq[Option[String]]
  ): Either[ArtifactError, File] =
    withTmpDir { dir =>
      val cache = FileCache[Task]((dir / "cache").toIO).copy(
        checksums = checksums,
        cachePolicies = Seq(CachePolicy.FetchMissing),
        retry = 0
      )
      cache.file(artifact).run.unsafeRun(wrapExceptions = true)(cache.ec)
    }

  val tests = Tests {

    test("parse") {
      test("default") {
        assert(CacheEnv.defaultChecksums(EnvValues(None, None)) == Seq(Some("SHA-1"), None))
        assert(CacheEnv.defaultChecksums(EnvValues(Some(" "), None)) == Seq(Some("SHA-1"), None))
      }
      test("strict") {
        val res = CacheEnv.defaultChecksums(EnvValues(Some("SHA-256,SHA-1"), None))
        assert(res == Seq(Some("SHA-256"), Some("SHA-1")))
      }
      test("env before prop") {
        val res = CacheEnv.defaultChecksums(EnvValues(Some("SHA-256"), Some("SHA-1,none")))
        assert(res == Seq(Some("SHA-256")))
        val fromProp = CacheEnv.defaultChecksums(EnvValues(None, Some("SHA-256,none")))
        assert(fromProp == Seq(Some("SHA-256"), None))
      }
      test("names are normalized") {
        // un-normalized names never match the keys of Artifact#checksumUrls, so that
        // `--checksum sha256` used to never find any checksum
        val res = CacheEnv.parseChecksums("sha256, SHA1,md5 ,sha-512,None,,SHA3-256")
        assert(res == Seq(
          Some("SHA-256"),
          Some("SHA-1"),
          Some("MD5"),
          Some("SHA-512"),
          None,
          Some("SHA3-256")
        ))
      }
    }

    test("the first available checksum type is the one checked") {
      test("SHA-256 when available") {
        withServer(Some(goodSha1), Some(goodSha256)) { artifact =>
          assert(run(artifact, Seq(Some("SHA-256"), Some("SHA-1"), None)).isRight)
        }
      }
      test("a wrong SHA-256 isn't ignored because SHA-1 is fine") {
        withServer(Some(goodSha1), Some(badSha256)) { artifact =>
          run(artifact, Seq(Some("SHA-256"), Some("SHA-1"), None)) match {
            case Left(err: ArtifactError.WrongChecksum) =>
              assert(err.sumType == "SHA-256")
            case other => sys.error(s"Expected a wrong checksum error, got $other")
          }
        }
      }
      test("else SHA-1") {
        withServer(Some(goodSha1), None) { artifact =>
          assert(run(artifact, Seq(Some("SHA-256"), Some("SHA-1"), None)).isRight)
        }
      }
    }

    test("missing checksums") {
      test("accepted with none") {
        withServer(None, None) { artifact =>
          assert(run(artifact, CacheEnv.defaultChecksums(EnvValues(None, None))).isRight)
        }
      }
      test("an error without none") {
        withServer(None, None) { artifact =>
          val checksums = CacheEnv.parseChecksums("SHA-256,SHA-1")
          run(artifact, checksums) match {
            case Left(err: ArtifactError.ChecksumErrors) =>
              assert(err.errors.map(_._1) == Seq("SHA-256", "SHA-1"))
            case other => sys.error(s"Expected checksum errors, got $other")
          }
        }
      }
    }
  }
}
