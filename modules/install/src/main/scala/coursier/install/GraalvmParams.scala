package coursier.install

import dataclass.data



@data case class GraalvmParams(
  defaultVersion: Option[String],
  extraNativeImageOptions: Seq[String]
)
