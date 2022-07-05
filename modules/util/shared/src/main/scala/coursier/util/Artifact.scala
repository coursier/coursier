package coursier.util

import coursier.core.Authentication
import dataclass.data

@data class Artifact(
  url: String,
  checksumUrls: Map[String, String] = Map.empty,
  extra: Map[String, Artifact] = Map.empty,
  changing: Boolean = false,
  optional: Boolean = false,
  authentication: Option[Authentication] = None
) {
  final override lazy val hashCode = tuple.hashCode()
}

object Artifact {

  /** Creates an artifact out of the passed URL, taking into account any "?changing" query string */
  def fromUrl(url: String): Artifact = {
    val (url0, changing) =
      if (url.endsWith("?changing")) (url.stripSuffix("?changing"), true)
      else if (url.endsWith("?changing=true")) (url.stripSuffix("?changing=true"), true)
      else if (url.endsWith("?changing=false")) (url.stripSuffix("?changing=false"), false)
      else (url, false)
    Artifact(url).withChanging(changing)
  }
}
