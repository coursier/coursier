package coursier.install

import dataclass.data

@data class GraalvmParams(
  extraNativeImageOptions: Seq[String]
)
