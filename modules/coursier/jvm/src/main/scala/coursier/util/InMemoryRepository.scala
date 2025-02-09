package coursier.util

import java.io.{File, FileNotFoundException, IOException}
import java.net.{URL, URLConnection}

import coursier.cache.{CacheUrl, ConnectionBuilder, FileCache}
import coursier.core._
import coursier.version.{Version => Version0, VersionInterval => VersionInterval0}
import dataclass.data

object InMemoryRepository {

  def forDependencies(dependencies: (Dependency, String)*): InMemoryRepository =
    InMemoryRepository(
      dependencies
        .map {
          case (dep, url) =>
            if (
              dep.versionConstraint.latest.nonEmpty || dep.versionConstraint.interval != VersionInterval0.zero || dep.versionConstraint.preferred.isEmpty
            )
              sys.error(
                s"InMemoryRepository dependencies should have an exact version (got ${dep.versionConstraint.asString} for ${dep.module.repr})"
              )
            val version = dep.versionConstraint.preferred.get
            ((dep.module, version), (new URL(url), version.repr.endsWith("SNAPSHOT")))
        }
        .toMap,
      None,
      false
    )

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

      def ifFile: Option[Boolean] =
        if (localArtifactsShouldBeCached && !new File(url.toURI).exists()) {
          val cachePath = cacheOpt.fold(coursier.cache.CacheDefaults.location)(_.location)
          // 'file' here stands for the protocol (e.g. it's https instead for https:// URLs)
          Some(new File(cachePath, s"file/${url.getPath}").exists())
        }
        else
          Some(new File(url.toURI).exists()) // FIXME Escaping / de-escaping needed here?

      def ifHttp: Option[Boolean] = {
        // HEAD request attempt, adapted from http://stackoverflow.com/questions/22541629/android-how-can-i-make-an-http-head-request/22545275#22545275

        var conn: URLConnection = null
        try {
          conn = ConnectionBuilder(url.toString)
            .withFollowHttpToHttpsRedirections(
              cacheOpt.fold(false)(_.followHttpToHttpsRedirections)
            )
            .withFollowHttpsToHttpRedirections(
              cacheOpt.fold(false)(_.followHttpsToHttpRedirections)
            )
            .withSslSocketFactoryOpt(cacheOpt.flatMap(_.sslSocketFactoryOpt))
            .withHostnameVerifierOpt(cacheOpt.flatMap(_.hostnameVerifierOpt))
            .withMethod("HEAD")
            .withMaxRedirectionsOpt(cacheOpt.flatMap(_.maxRedirections))
            .connection()
          // Even though the finally clause handles this too, this has to be run here, so that we return Some(true)
          // iff this doesn't throw.
          conn.getInputStream.close()
          Some(true)
        }
        catch {
          case _: FileNotFoundException => Some(false)
          case _: IOException           => None // error other than not found
        }
        finally if (conn != null)
            CacheUrl.closeConn(conn)
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
      finally if (conn != null)
          CacheUrl.closeConn(conn)
    }

    protocolSpecificAttemptOpt
      .getOrElse(genericAttempt)
  }

  @deprecated("Use the override accepting a cache", "2.0.0-RC3")
  def apply(
    fallbacks: Map[(Module, String), (URL, Boolean)]
  ): InMemoryRepository =
    privateApply(
      fallbacks.map {
        case ((m, v), (url, thing)) =>
          ((m, Version0(v)), (url, thing))
      }
    )

  private[coursier] def privateApply(
    fallbacks: Map[(Module, Version0), (URL, Boolean)]
  ): InMemoryRepository =
    new InMemoryRepository(fallbacks, None, localArtifactsShouldBeCached = false)

  @deprecated("Use the override accepting a cache", "2.0.0-RC3")
  def apply(
    fallbacks: Map[(Module, String), (URL, Boolean)],
    localArtifactsShouldBeCached: Boolean
  ): InMemoryRepository =
    privateApply(
      fallbacks.map {
        case ((m, v), (url, thing)) =>
          ((m, Version0(v)), (url, thing))
      },
      localArtifactsShouldBeCached
    )

  private[coursier] def privateApply(
    fallbacks: Map[(Module, Version0), (URL, Boolean)],
    localArtifactsShouldBeCached: Boolean
  ): InMemoryRepository =
    new InMemoryRepository(fallbacks, None, localArtifactsShouldBeCached)

  def create[F[_]](
    fallbacks: Map[(Module, Version0), (URL, Boolean)],
    cache: FileCache[F]
  ): InMemoryRepository =
    new InMemoryRepository(
      fallbacks,
      Some(cache.asInstanceOf[FileCache[Nothing]]),
      localArtifactsShouldBeCached = cache.localArtifactsShouldBeCached
    )

  @deprecated("Use create instead", "2.1.25")
  def apply[F[_]](
    fallbacks: Map[(Module, String), (URL, Boolean)],
    cache: FileCache[F]
  ): InMemoryRepository =
    create(
      fallbacks.map {
        case ((mod, ver), value) =>
          ((mod, Version0(ver)), value)
      },
      cache
    )

  def apply(
    fallbacks0: Map[(Module, String), (URL, Boolean)],
    localArtifactsShouldBeCached: Boolean,
    cacheOpt: Option[FileCache[Nothing]]
  ): InMemoryRepository =
    InMemoryRepository(
      fallbacks0.map {
        case ((mod, ver), value) =>
          ((mod, Version0(ver)), value)
      },
      cacheOpt,
      localArtifactsShouldBeCached
    )

}

@data class InMemoryRepository(
  fallbacks0: Map[(Module, Version0), (URL, Boolean)],
  cacheOpt: Option[FileCache[Nothing]],
  localArtifactsShouldBeCached: Boolean
) extends Repository with Repository.VersionApi {

  def this(
    fallbacks0: Map[(Module, String), (URL, Boolean)],
    localArtifactsShouldBeCached: Boolean,
    cacheOpt: Option[FileCache[Nothing]]
  ) = this(
    fallbacks0.map {
      case ((mod, ver), value) =>
        ((mod, Version0(ver)), value)
    },
    cacheOpt,
    localArtifactsShouldBeCached
  )

  @deprecated("Use fallbacks0 instead", "2.1.25")
  def fallbacks: Map[(Module, String), (URL, Boolean)] =
    fallbacks0.map {
      case ((mod, ver), value) =>
        ((mod, ver.asString), value)
    }
  @deprecated("Use withFallbacks0 instead", "2.1.25")
  def withFallbacks(newFallbacks: Map[(Module, String), (URL, Boolean)]): InMemoryRepository =
    withFallbacks0(
      newFallbacks.map {
        case ((mod, ver), value) =>
          ((mod, Version0(ver)), value)
      }
    )

  override def find0[F[_]](
    module: Module,
    version: Version0,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (ArtifactSource, Project)] = {

    val res = fallbacks0
      .get((module, version))
      .fold[Either[String, (ArtifactSource, Project)]](Left("No fallback URL found")) {
        case (url, _) =>
          val urlStr = url.toExternalForm
          val idx    = urlStr.lastIndexOf('/')

          if (idx < 0 || urlStr.endsWith("/"))
            Left(s"$url doesn't point to a file")
          else {
            val (dirUrlStr, fileName) = urlStr.splitAt(idx + 1)

            if (InMemoryRepository.exists(url, localArtifactsShouldBeCached, cacheOpt)) {
              val proj = Project(
                module,
                version,
                Nil,
                Map.empty[Configuration, Seq[Configuration]],
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
            }
            else
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
    dependency.versionConstraint.preferred match {
      case Some(version) =>
        fallbacks0
          .get((dependency.module, version))
          .toSeq
          .map {
            case (url, changing) =>
              val url0 = url.toString
              val ext  = url0.substring(url0.lastIndexOf('.') + 1)
              val pub = Publication(
                dependency.module.name.value, // ???
                Type(ext),
                Extension(ext),
                Classifier.empty
              )
              (pub, Artifact(url0, Map.empty, Map.empty, changing, optional = false, None))
          }
      case None =>
        Nil
    }

}
