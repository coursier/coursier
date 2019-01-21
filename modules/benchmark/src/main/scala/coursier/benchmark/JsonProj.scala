package coursier.benchmark

import argonaut.Json
import coursier.core.Project

object JsonProj {

  def write(proj: Project): String =
    JsonProjCodecs.encoder(proj).nospaces

  def read(content: String): Project = {
    import argonaut._, Argonaut._
    content.decodeEither(JsonProjCodecs.decoder) match {
      case Left(e) => sys.error(s"Decoding: $e")
      case Right(p) => p
    }
  }

  def readJson(content: String): Json = {
    import argonaut._, Argonaut._
    content.parse match {
      case Left(e) => sys.error(s"Decoding: $e")
      case Right(p) => p
    }
  }

}
