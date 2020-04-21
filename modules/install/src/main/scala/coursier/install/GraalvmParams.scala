package coursier.install

import dataclass.data

@data class GraalvmParams(
  defaultVersion: Option[String],
  extraNativeImageOptions: Seq[String]
)
