package coursier.core

import coursier.core.compatibility.encodeURIComponent
import coursier.util.{EitherT, Monad}

trait Repository extends Product with Serializable with Artifact.Source {
  def find[F[_]](
    module: Module,
    version: String,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Artifact.Source, Project)]
}

object Repository {

  type Fetch[F[_]] = Artifact => EitherT[F, String, String]


  implicit class ArtifactExtensions(val underlying: Artifact) extends AnyVal {
    def withDefaultChecksums: Artifact =
      underlying.copy(checksumUrls = underlying.checksumUrls ++ Seq(
        "MD5" -> (underlying.url + ".md5"),
        "SHA-1" -> (underlying.url + ".sha1"),
        "SHA-256" -> (underlying.url + ".sha256")
      ))
    def withDefaultSignature: Artifact =
      underlying.copy(extra = underlying.extra ++ Seq(
        "sig" ->
          Artifact(
            underlying.url + ".asc",
            Map.empty,
            Map.empty,
            changing = underlying.changing,
            optional = true,
            authentication = underlying.authentication
          )
            .withDefaultChecksums
      ))
  }
}

