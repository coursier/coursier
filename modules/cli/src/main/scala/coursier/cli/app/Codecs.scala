package coursier.cli.app

import argonaut.{DecodeJson, DecodeResult, EncodeJson, Json, JsonObject}

private[coursier] object Codecs {

  implicit val encodeObj: EncodeJson[JsonObject] =
    EncodeJson(Json.jObject)
  implicit val decodeObj: DecodeJson[JsonObject] =
    DecodeJson { c =>
      c.focus.obj match {
        case Some(obj) => DecodeResult.ok(obj)
        case None => DecodeResult.fail("Expected object", c.history)
      }
    }

}
