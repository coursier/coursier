package coursier.tests

import java.io._
import java.nio.charset.StandardCharsets.UTF_8

import coursier.cache.ConnectionBuilder
import coursier.core.{Repository, ResolutionProcess}
import coursier.util.{EitherT, Sync, Task}

import scala.util.{Failure, Success, Try}

object Platform {

  def readFullySync(is: InputStream) = {
    val buffer = new ByteArrayOutputStream()
    val data   = Array.ofDim[Byte](16384)

    var nRead = is.read(data, 0, data.length)
    while (nRead != -1) {
      buffer.write(data, 0, nRead)
      nRead = is.read(data, 0, data.length)
    }

    buffer.flush()
    buffer.toByteArray
  }

  def readFully[F[_]: Sync](is: => InputStream): F[Either[String, String]] =
    Sync[F].delay {
      val t = Try {
        val is0 = is
        val b =
          try readFullySync(is0)
          finally is0.close()

        new String(b, UTF_8)
      }

      t match {
        case Success(r) => Right(r)
        case Failure(e: java.io.FileNotFoundException) if e.getMessage != null =>
          Left(s"Not found: ${e.getMessage}")
        case Failure(e) =>
          Left(s"$e${Option(e.getMessage).fold("")(" (" + _ + ")")}")
      }
    }

  val artifact: Repository.Fetch[Task] = { artifact =>
    EitherT {
      val conn = ConnectionBuilder(artifact.url)
        .withAuthentication(artifact.authentication)
        .connection()
      readFully[Task](conn.getInputStream)
    }
  }

  def fetch(
    repositories: Seq[Repository]
  ): ResolutionProcess.Fetch[Task] =
    ResolutionProcess.fetch(repositories, Platform.artifact)

}
