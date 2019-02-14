package coursier.cache

import java.io.{ByteArrayOutputStream, File, FileInputStream, InputStream}
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.ExecutorService

import coursier.cache.internal.MockCacheEscape
import coursier.core.{Artifact, Repository}
import coursier.util.{EitherT, Schedulable}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

final case class MockCache[F[_]](
  base: Path,
  writeMissing: Boolean,
  pool: ExecutorService,
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

      val path = base.resolve(MockCacheEscape.urlAsPath(artifact.url))

      val init0 = S.bind[Boolean, Either[ArtifactError, Unit]](S.schedule(pool)(Files.exists(path))) {
        case true => S.point(Right(()))
        case false =>
          if (writeMissing) {
            val f = S.schedule[Either[ArtifactError, Unit]](pool) {
              Files.createDirectories(path.getParent)
              def is() = CacheUrl.urlConnection(artifact.url, artifact.authentication).getInputStream
              val b = MockCache.readFullySync(is())
              Files.write(path, b)
              Right(())
            }

            S.handle(f) {
              case e: Exception =>
                Left(ArtifactError.DownloadError(e.toString))
            }
          } else
            S.point(Left(ArtifactError.NotFound(path.toString)))
      }

      EitherT[F, ArtifactError, Unit](init0)
        .map(_ => path.toFile)
    }
  }

  lazy val ec = ExecutionContext.fromExecutorService(pool)

}

object MockCache {

  def create[F[_]: Schedulable](
    base: Path,
    writeMissing: Boolean = false,
    pool: ExecutorService = CacheDefaults.pool
  ): MockCache[F] =
    MockCache(
      base,
      writeMissing,
      pool,
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

}
