package coursier

import java.io._
import java.nio.charset.StandardCharsets.UTF_8

import coursier.util.{EitherT, Task}

import scala.util.{Failure, Success, Try}

object Platform {

  def readFullySync(is: InputStream) = {
    val buffer = new ByteArrayOutputStream()
    val data = Array.ofDim[Byte](16384)

    var nRead = is.read(data, 0, data.length)
    while (nRead != -1) {
      buffer.write(data, 0, nRead)
      nRead = is.read(data, 0, data.length)
    }

    buffer.flush()
    buffer.toByteArray
  }

  def readFully(is: => InputStream): Task[Either[String, String]] =
    Task.delay {
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

  val artifact: Fetch.Content[Task] = { artifact =>
    EitherT {
      val conn = Cache.urlConnection(artifact.url, artifact.authentication)
      readFully(conn.getInputStream)
    }
  }

  def fetch(
    repositories: Seq[core.Repository]
  ): Fetch.Metadata[Task] =
    Fetch.from(repositories, Platform.artifact)

}
