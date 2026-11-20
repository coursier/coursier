package coursier.cache

import coursier.cache.TestUtil._
import coursier.credentials.{DirectCredentials, NetrcCredentials}
import coursier.util.{Artifact, Task}
import utest._

import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

/** Credentials from a netrc file, sent like curl sends them: whatever the scheme and port */
object FileCacheNetrcTests extends TestSuite {

  private val jarContent = "the artifact".getBytes(UTF_8)

  private def basic(user: String, password: String): String =
    "Basic " + Base64.getEncoder.encodeToString(s"$user:$password".getBytes(UTF_8))

  private def challenging(expected: String)(entry: RequestLog.Entry): RawHttpServer.Response =
    if (entry.header("Authorization").contains(expected)) RawHttpServer.ok(jarContent)
    else
      RawHttpServer.Response(
        "HTTP/1.1 401 Unauthorized",
        Seq(
          "WWW-Authenticate" -> """Basic realm="netrc realm"""",
          "Content-Length"   -> "0"
        )
      )

  private def run(dir: os.Path, netrc: String, url: String, others: DirectCredentials*) = {
    os.write(dir / "netrc", netrc)
    val cache = FileCache[Task]((dir / "cache").toIO).copy(
      checksums = Nil,
      cachePolicies = Seq(CachePolicy.FetchMissing),
      credentials = others ++ Seq(NetrcCredentials(Some((dir / "netrc").toString)))
    )
    cache.file(Artifact(url)).run.unsafeRun(wrapExceptions = true)(cache.ec)
  }

  val tests = Tests {

    test("sent over plain HTTP, on any port, once the server asks") {
      val log = new RequestLog
      RawHttpServer.withServer(log)(challenging(basic("alex", "s3cret"))) { baseUrl =>
        withTmpDir { dir =>
          val res = run(
            dir,
            "machine localhost login alex password s3cret\n",
            s"$baseUrl/dir/foo.jar"
          )
          assert(res.isRight)
          assert(log.methods == List("GET", "GET"))
          assert(log.entries.head.header("Authorization").isEmpty)
        }
      }
    }

    test("default entry") {
      val log = new RequestLog
      RawHttpServer.withServer(log)(challenging(basic("anon", "an0n"))) { baseUrl =>
        withTmpDir { dir =>
          val res = run(
            dir,
            "machine other.example.com login alex password s3cret\ndefault login anon password an0n\n",
            s"$baseUrl/dir/foo.jar"
          )
          assert(res.isRight)
        }
      }
    }

    test("other credentials win") {
      val log = new RequestLog
      RawHttpServer.withServer(log)(challenging(basic("bob", "b0b"))) { baseUrl =>
        withTmpDir { dir =>
          val res = run(
            dir,
            "machine localhost login alex password s3cret\n",
            s"$baseUrl/dir/foo.jar",
            DirectCredentials("localhost", "bob", "b0b").copy(httpsOnly = false)
          )
          assert(res.isRight)
        }
      }
    }

    test("no matching entry") {
      val log = new RequestLog
      RawHttpServer.withServer(log)(challenging(basic("alex", "s3cret"))) { baseUrl =>
        withTmpDir { dir =>
          val res = run(
            dir,
            "machine other.example.com login alex password s3cret\n",
            s"$baseUrl/dir/foo.jar"
          )
          assert(res.isLeft)
          assert(log.entries.forall(_.header("Authorization").isEmpty))
        }
      }
    }
  }
}
