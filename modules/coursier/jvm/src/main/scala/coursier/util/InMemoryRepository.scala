package coursier.util

import java.io.{File, FileNotFoundException, IOException}
import java.net.{URL, URLConnection}

import coursier.cache.{CacheUrl, FileCache}
import coursier.core._

object InMemoryRepository {

  @deprecated("Use the override accepting a cache", "2.0.0-RC3")
  def exists(
    url: URL,
    localArtifactsShouldBeCached: Boolean
  ): Boolean =
    exists(url, localArtifactsShouldBeCached, None)

  def exists(
    url: URL,
    localArtifactsShouldBeCached: Boolean,
    cacheOpt: Option[FileCache[Nothing]]
  ): Boolean = {

    // Sometimes HEAD attempts fail even though standard GETs are fine.
    // E.g. https://github.com/NetLogo/NetLogo/releases/download/5.3.1/NetLogo.jar
    // returning 403s. Hence the second attempt below.

    val protocolSpecificAttemptOpt = {

      def ifFile: Option[Boolean] = {
        if (localArtifactsShouldBeCached && !new File(url.toURI).exists()) {
          val cachePath = cacheOpt.fold(coursier.cache.CacheDefaults.location)(_.location)
          // 'file' here stands for the protocol (e.g. it's https instead for https:// URLs)
          Some(new File(cachePath, s"file/${url.getPath}").exists())
        } else {
          Some(new File(url.toURI).exists()) // FIXME Escaping / de-escaping needed here?
        }
      }

      def ifHttp: Option[Boolean] = {
        // HEAD request attempt, adapted from http://stackoverflow.com/questions/22541629/android-how-can-i-make-an-http-head-request/22545275#22545275

        var conn: URLConnection = null
        try {
          conn = CacheUrl.urlConnection(
            url.toString,
            None,
            followHttpToHttpsRedirections = cacheOpt.fold(false)(_.followHttpToHttpsRedirections),
            followHttpsToHttpRedirections = cacheOpt.fold(false)(_.followHttpsToHttpRedirections),
            sslSocketFactoryOpt = cacheOpt.flatMap(_.sslSocketFactoryOpt),
            hostnameVerifierOpt = cacheOpt.flatMap(_.hostnameVerifierOpt),
            method = "HEAD",
            maxRedirectionsOpt = cacheOpt.flatMap(_.maxRedirections)
          )
          Some(true)
        }
        catch {
          case _: FileNotFoundException => Some(false)
          case _: IOException           => None // error other than not found
        }
        finally {
          if (conn != null)
            CacheUrl.closeConn(conn)
        }
      }

      url.getProtocol match {
        case "file"           => ifFile
        case "http" | "https" => ifHttp
        case _                => None
      }
    }

    def genericAttempt: Boolean = {
      var conn: URLConnection = null
      try {
        conn = url.openConnection()
        // NOT setting request type to HEAD here.
        conn.getInputStream.close()
        true
      }
      catch {
        case _: IOException => false
      }
      finally {
        if (conn != null)
          CacheUrl.closeConn(conn)
      }
    }

    protocolSpecificAttemptOpt
      .getOrElse(genericAttempt)
  }

  @deprecated("Use the override accepting a cache", "2.0.0-RC3")
  def apply(
    fallbacks: Map[(Module, String), (URL, Boolean)]
  ): InMemoryRepository =
    new InMemoryRepository(fallbacks, localArtifactsShouldBeCached = false, None)

  @deprecated("Use the override accepting a cache", "2.0.0-RC3")
  def apply(
    fallbacks: Map[(Module, String), (URL, Boolean)],
    localArtifactsShouldBeCached: Boolean
  ): InMemoryRepository =
    new InMemoryRepository(fallbacks, localArtifactsShouldBeCached, None)

  def apply[F[_]](
    fallbacks: Map[(Module, String), (URL, Boolean)],
    cache: FileCache[F]
  ): InMemoryRepository =
    new InMemoryRepository(
      fallbacks,
      localArtifactsShouldBeCached = cache.localArtifactsShouldBeCached,
      Some(cache.asInstanceOf[FileCache[Nothing]])
    )

}

final class InMemoryRepository private (
  val fallbacks: Map[(Module, String), (URL, Boolean)],
  val localArtifactsShouldBeCached: Boolean,
  val cacheOpt: Option[FileCache[Nothing]]
) extends Repository {

  def find[F[_]](
    module: Module,
    version: String,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Artifact.Source, Project)] = {

    val res = fallbacks
      .get((module, version))
      .fold[Either[String, (Artifact.Source, Project)]](Left("No fallback URL found")) {
        case (url, _) =>

          val urlStr = url.toExternalForm
          val idx = urlStr.lastIndexOf('/')

          if (idx < 0 || urlStr.endsWith("/"))
            Left(s"$url doesn't point to a file")
          else {
            val (dirUrlStr, fileName) = urlStr.splitAt(idx + 1)

            if (InMemoryRepository.exists(url, localArtifactsShouldBeCached, cacheOpt)) {
              val proj = Project(
                module,
                version,
                Nil,
                Map.empty,
                None,
                Nil,
                Nil,
                Nil,
                None,
                None,
                None,
                relocated = false,
                None,
                Nil,
                Info.empty
              )

              Right((this, proj))
            } else
              Left(s"$fileName not found under $dirUrlStr")
          }
      }

    EitherT(F.point(res))
  }

  def artifacts(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[Classifier]]
  ): Seq[(Publication, Artifact)] =
    fallbacks
      .get(dependency.moduleVersion)
      .toSeq
      .map {
        case (url, changing) =>
          val url0 = url.toString
          val ext = url0.substring(url0.lastIndexOf('.') + 1)
          val pub = Publication(
            dependency.module.name.value, // ???
            Type(ext),
            Extension(ext),
            Classifier.empty
          )
          (pub, Artifact(url0, Map.empty, Map.empty, changing, optional = false, None))
      }

}
