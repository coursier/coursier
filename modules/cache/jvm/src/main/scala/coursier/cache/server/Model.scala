package coursier.cache.server

import com.github.plokhotnyuk.jsoniter_scala.core._
import com.github.plokhotnyuk.jsoniter_scala.macros._
import coursier.cache.CachePolicy

object Model {

  def parseCachePolicy(name: String): Option[CachePolicy] =
    name match {
      case "LocalOnly"           => Some(CachePolicy.LocalOnly)
      case "LocalOnlyIfValid"    => Some(CachePolicy.LocalOnlyIfValid)
      case "LocalUpdateChanging" => Some(CachePolicy.LocalUpdateChanging)
      case "LocalUpdate"         => Some(CachePolicy.LocalUpdate)
      case "UpdateChanging"      => Some(CachePolicy.UpdateChanging)
      case "Update"              => Some(CachePolicy.Update)
      case "FetchMissing"        => Some(CachePolicy.FetchMissing)
      case "ForceDownload"       => Some(CachePolicy.ForceDownload)
      case _                     => None
    }

  final case class GetRequest(
    artifact: Artifact,
    cachePolicy: Option[String] = None
  )

  final case class GetResponse(
    path: Option[String],
    error: Option[SerializedArtifactError]
  )

  final case class PathRequest(
    artifact: Artifact
  )

  final case class PathResponse(
    path: Option[String],
    error: Option[SerializedArtifactError]
  )

  final case class Artifact(
    url: String,
    checksumUrls: Map[String, String] = Map.empty,
    extra: Map[String, Artifact] = Map.empty,
    changing: Boolean = false,
    optional: Boolean = false
  ) {
    def toArtifact: coursier.util.Artifact =
      coursier.util.Artifact(
        url = url,
        checksumUrls = checksumUrls,
        extra = extra.map { case (k, v) => k -> v.toArtifact },
        changing = changing,
        optional = optional,
        authentication = None
      )
  }

  object Artifact {
    def fromArtifact(artifact: coursier.util.Artifact): Artifact =
      Artifact(
        url = artifact.url,
        checksumUrls = artifact.checksumUrls,
        extra = artifact.extra.map { case (k, v) => k -> fromArtifact(v) },
        changing = artifact.changing,
        optional = artifact.optional
      )
  }

  final case class SerializedException(
    className: String,
    message: String,
    stackTrace: Seq[SerializedStackTraceElement],
    cause: Option[SerializedException]
  )

  object SerializedException {
    def fromThrowable(t: Throwable): SerializedException =
      SerializedException(
        className = t.getClass.getName,
        message = Option(t.getMessage).getOrElse(""),
        stackTrace = t.getStackTrace.toSeq.map { ste =>
          SerializedStackTraceElement(
            className = ste.getClassName,
            methodName = ste.getMethodName,
            fileName = Option(ste.getFileName),
            lineNumber = ste.getLineNumber
          )
        },
        cause = Option(t.getCause).map(fromThrowable)
      )
  }

  final case class SerializedStackTraceElement(
    className: String,
    methodName: String,
    fileName: Option[String],
    lineNumber: Int
  )

  final case class SerializedArtifactError(
    `type`: String,
    message: String,
    exception: SerializedException
  )

  implicit val getRequestCodec: JsonValueCodec[GetRequest] =
    JsonCodecMaker.make(CodecMakerConfig.withAllowRecursiveTypes(true))

  implicit val getResponseCodec: JsonValueCodec[GetResponse] =
    JsonCodecMaker.make(CodecMakerConfig.withAllowRecursiveTypes(true))

  implicit val pathRequestCodec: JsonValueCodec[PathRequest] =
    JsonCodecMaker.make(CodecMakerConfig.withAllowRecursiveTypes(true))

  implicit val pathResponseCodec: JsonValueCodec[PathResponse] =
    JsonCodecMaker.make(CodecMakerConfig.withAllowRecursiveTypes(true))

}
