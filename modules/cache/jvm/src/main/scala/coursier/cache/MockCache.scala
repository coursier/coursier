package coursier.cache

import java.io._
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.concurrent.{ConcurrentHashMap, ExecutorService}
import java.util.zip.{GZIPOutputStream, ZipEntry, ZipInputStream, ZipOutputStream}

import coursier.cache.internal.MockCacheEscape
import coursier.paths.Util
import coursier.util.{Artifact, EitherT, Sync, WebPage}
import coursier.util.Monad.ops._
import dataclass._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

// format: off
@data class MockCache[F[_]](
  base: Path,
  extraData: Seq[Path],
  writeMissing: Boolean,
  pool: ExecutorService,
  S: Sync[F],
  dummyArtifact: Artifact => Boolean = _ => false,
  @since
    proxy: Option[java.net.Proxy] = None,
  @since("2.1.25")
    baseChangingOpt: Option[Path] = None,
  replaceByNames: Artifact => Boolean = _ => false,
  failsWhenWritingMissing: ConcurrentHashMap[String, ArtifactError] = new ConcurrentHashMap[String, ArtifactError]
) extends Cache[F] {
// format: on

  private implicit def S0: Sync[F] = S

  def fetch: Cache.Fetch[F] = { artifact =>

    val (artifact0, links) =
      if (artifact.url.endsWith("/.links"))
        (artifact.withUrl(artifact.url.stripSuffix(".links")), true)
      else
        (artifact, false)

    if (proxy.nonEmpty || artifact0.url.startsWith("http://localhost:"))
      EitherT(MockCache.readFully(
        ConnectionBuilder(artifact0.url)
          .withAuthentication(artifact0.authentication)
          .withProxy(proxy)
          .connection()
          .getInputStream,
        if (links) Some(artifact0.url) else None
      ))
    else
      file(artifact0)
        .leftMap(_.describe)
        .flatMap { f =>
          EitherT {
            MockCache.readFully(
              Files.newInputStream(f.toPath),
              if (links) Some(artifact0.url) else None
            )
          }
        }
  }

  def file(artifact: Artifact): EitherT[F, ArtifactError, File] =
    if (artifact.url.startsWith("file:")) {
      val url =
        if (artifact.url.endsWith("/"))
          artifact.url + ".directory"
        else
          artifact.url
      val f = new File(new URI(url))
      EitherT.point(f)
    }
    else {

      val base0 =
        if (artifact.changing) baseChangingOpt.getOrElse(base)
        else base

      assert(artifact.authentication.isEmpty)

      val path = base0.resolve(MockCacheEscape.urlAsPath(artifact.url))

      val fromExtraData = extraData.foldLeft(S.point(Option.empty[Path])) {
        (acc, p) =>
          acc.flatMap {
            case Some(_) => acc
            case None =>
              val path = p.resolve(MockCacheEscape.urlAsPath(artifact.url))
              S.schedule(pool)(Files.exists(path)).map {
                case true  => Some(path)
                case false => None
              }
          }
      }

      val init0 = S.schedule(pool)(Files.exists(path)).flatMap {
        case true => S.point(Right(path)): F[Either[ArtifactError, Path]]
        case false =>
          val res: F[Either[ArtifactError, Path]] =
            if (writeMissing) {
              val f: F[Either[ArtifactError, Path]] =
                Option(failsWhenWritingMissing.get(artifact.url)) match {
                  case Some(cachedError) =>
                    S.point(Left(cachedError))
                  case None =>
                    S.schedule[Either[ArtifactError, Path]](pool) {
                      Util.createDirectories(path.getParent)
                      def is(): InputStream =
                        if (dummyArtifact(artifact))
                          new ByteArrayInputStream(Array.emptyByteArray)
                        else
                          ConnectionBuilder(artifact.url)
                            .withAuthentication(artifact.authentication)
                            .connection()
                            .getInputStream
                      val b = MockCache.readFullySync(is())
                      val finalContent =
                        if (replaceByNames(artifact)) {
                          val name = artifact.url.drop(artifact.url.lastIndexOf("/") + 1)
                          if (artifact.url.endsWith(".gz")) {
                            val baos = new ByteArrayOutputStream
                            val gzos = new GZIPOutputStream(baos)
                            gzos.write((artifact.url + "!" + name.stripSuffix(".gz")).getBytes(
                              StandardCharsets.UTF_8
                            ))
                            gzos.finish()
                            gzos.flush()
                            baos.toByteArray
                          }
                          else if (artifact.url.endsWith(".zip")) {
                            val zis           = new ZipInputStream(new ByteArrayInputStream(b))
                            val baos          = new ByteArrayOutputStream
                            val zos           = new ZipOutputStream(baos)
                            var ent: ZipEntry = null
                            while ({
                              ent = zis.getNextEntry
                              ent != null
                            }) {
                              val ent0 = new ZipEntry(ent.getName)
                              zos.putNextEntry(ent0)
                              if (!ent.getName.endsWith("/")) {
                                zos.write((artifact.url + "!" + ent.getName).getBytes(
                                  StandardCharsets.UTF_8
                                ))
                                zos.flush()
                                zos.closeEntry()
                              }
                            }
                            zos.finish()
                            zos.flush()
                            baos.toByteArray
                          }
                          else
                            artifact.url.getBytes(StandardCharsets.UTF_8)
                        }
                        else
                          b
                      Files.write(path, finalContent)
                      Right(path)
                    }
                }

              val f0 = S.handle(f) {
                case _: FileNotFoundException =>
                  Left(new ArtifactError.NotFound(artifact.url))
                case e: Exception =>
                  Left(new ArtifactError.DownloadError(e.toString, Some(e)))
              }
              f0.map {
                case Left(err) =>
                  failsWhenWritingMissing.putIfAbsent(artifact.url, err)
                  Left(err)
                case Right(path) =>
                  Right(path)
              }
            }
            else
              S.point(Left(new ArtifactError.NotFound(path.toString)))
          res
      }

      val e = fromExtraData.flatMap {
        case None    => init0
        case Some(f) => S.point(Right(f)): F[Either[ArtifactError, Path]]
      }
      EitherT[F, ArtifactError, Path](e)
        .map(_.toFile)
    }

  lazy val ec = ExecutionContext.fromExecutorService(pool)

}

object MockCache {

  def create[F[_]: Sync](
    base: Path,
    pool: ExecutorService,
    extraData: Seq[Path] = Nil,
    writeMissing: Boolean = false,
    replaceByNames: Artifact => Boolean = _ => false,
    baseChangingOpt: Option[Path]
  ): MockCache[F] =
    MockCache(
      base,
      extraData,
      writeMissing,
      pool,
      Sync[F],
      dummyArtifact = _ => false,
      replaceByNames = replaceByNames,
      proxy = None,
      baseChangingOpt = baseChangingOpt,
      failsWhenWritingMissing = new ConcurrentHashMap[String, ArtifactError]
    )

  def create[F[_]: Sync](
    base: Path,
    pool: ExecutorService,
    extraData: Seq[Path],
    writeMissing: Boolean
  ): MockCache[F] =
    MockCache(
      base,
      extraData,
      writeMissing,
      pool,
      Sync[F]
    )

  private def readFullySync(is: InputStream) = {
    val buffer = new ByteArrayOutputStream
    val data   = Array.ofDim[Byte](16384)

    var nRead = is.read(data, 0, data.length)
    while (nRead != -1) {
      buffer.write(data, 0, nRead)
      nRead = is.read(data, 0, data.length)
    }

    buffer.flush()
    buffer.toByteArray
  }

  private def readFully[F[_]: Sync](
    is: => InputStream,
    parseLinksUrl: Option[String]
  ): F[Either[String, String]] =
    Sync[F].delay {
      val t = Try {
        val is0 = is
        val b =
          try readFullySync(is0)
          finally is0.close()

        val s = new String(b, StandardCharsets.UTF_8)
        parseLinksUrl match {
          case None => s
          case Some(url) =>
            WebPage.listElements(url, s).mkString("\n")
        }
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
