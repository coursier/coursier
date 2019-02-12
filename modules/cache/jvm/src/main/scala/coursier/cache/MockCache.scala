package coursier.cache

import java.io.{ByteArrayOutputStream, File, FileInputStream, InputStream}
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.ExecutorService

import coursier.core.{Artifact, Repository}
import coursier.util.{EitherT, Schedulable}

import scala.util.{Failure, Success, Try}

final case class MockCache[F[_]](
  base: Path,
  writeMissing: Boolean,
  S: Schedulable[F]
) extends Cache[F] {

  private implicit def S0 = S

  def fetch: Repository.Fetch[F] = { artifact =>

    if (artifact.url.startsWith("http://localhost:"))
      EitherT(MockCache.readFully(
        CacheUrl.urlConnection(artifact.url, artifact.authentication).getInputStream
      ))
    else
      file(artifact)
        .leftMap(_.describe)
        .flatMap { f =>
          EitherT(MockCache.readFully(new FileInputStream(f)))
        }
  }

  def fetchs: Seq[Repository.Fetch[F]] =
    Seq(fetch)

  def file(artifact: Artifact): EitherT[F, ArtifactError, File] = {

    if (artifact.url.startsWith("file:/"))
      EitherT.point(new File(new URI(artifact.url)))
    else {

      assert(artifact.authentication.isEmpty)

      val path = base.resolve(MockCache.urlAsPath(artifact.url))

      val init = EitherT[F, ArtifactError, Unit] {
        if (Files.exists(path))
          Schedulable[F].point(Right(()))
        else if (writeMissing) {
          val f = Schedulable[F].delay[Either[ArtifactError, Unit]] {
            Files.createDirectories(path.getParent)
            def is() = CacheUrl.urlConnection(artifact.url, artifact.authentication).getInputStream
            val b = MockCache.readFullySync(is())
            Files.write(path, b)
            Right(())
          }

          Schedulable[F].handle(f) {
            case e: Exception =>
              Left(ArtifactError.DownloadError(e.toString))
          }
        } else
          Schedulable[F].point(Left(ArtifactError.NotFound(path.toString)))
      }

      init.map { _ =>
        path.toFile
      }
    }
  }

  def pool: ExecutorService =
    ???
}

object MockCache {
  def create[F[_]: Schedulable](
    base: Path,
    writeMissing: Boolean
  ): MockCache[F] =
    MockCache(
      base,
      writeMissing,
      Schedulable[F]
    )


  private def readFullySync(is: InputStream) = {
    val buffer = new ByteArrayOutputStream
    val data = Array.ofDim[Byte](16384)

    var nRead = is.read(data, 0, data.length)
    while (nRead != -1) {
      buffer.write(data, 0, nRead)
      nRead = is.read(data, 0, data.length)
    }

    buffer.flush()
    buffer.toByteArray
  }

  private def readFully[F[_]: Schedulable](is: => InputStream): F[Either[String, String]] =
    Schedulable[F].delay {
      val t = Try {
        val is0 = is
        val b =
          try readFullySync(is0)
          finally is0.close()

        new String(b, StandardCharsets.UTF_8)
      }

      t match {
        case Success(r) => Right(r)
        case Failure(e: java.io.FileNotFoundException) if e.getMessage != null =>
          Left(s"Not found: ${e.getMessage}")
        case Failure(e) =>
          Left(s"$e${Option(e.getMessage).fold("")(" (" + _ + ")")}")
      }
    }

  private val unsafeChars: Set[Char] = " %$&+,:;=?@<>#".toSet

  // Scala version of http://stackoverflow.com/questions/4571346/how-to-encode-url-to-avoid-special-characters-in-java/4605848#4605848
  // '/' was removed from the unsafe character list
  private def escape(input: String): String = {

    def toHex(ch: Int) =
      (if (ch < 10) '0' + ch else 'A' + ch - 10).toChar

    def isUnsafe(ch: Char) =
      ch > 128 || ch < 0 || unsafeChars(ch)

    input.flatMap {
      case ch if isUnsafe(ch) =>
        "%" + toHex(ch / 16) + toHex(ch % 16)
      case other =>
        other.toString
    }
  }

  private def urlAsPath(url: String): String = {

    assert(!url.startsWith("file:/"), s"Got file URL: $url")

    url.split(":", 2) match {
      case Array(protocol, remaining) =>
        val remaining0 =
          if (remaining.startsWith("///"))
            remaining.stripPrefix("///")
          else if (remaining.startsWith("/"))
            remaining.stripPrefix("/")
          else
            throw new Exception(s"URL $url doesn't contain an absolute path")

        val remaining1 =
          if (remaining0.endsWith("/"))
            // keeping directory content in .directory files
            remaining0 + ".directory"
          else
            remaining0

        escape(protocol + "/" + remaining1.dropWhile(_ == '/'))

      case _ =>
        throw new Exception(s"No protocol found in URL $url")
    }
  }

}
