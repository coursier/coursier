package coursier.install

import dataclass.data

@data(
  deprecatedSetters = true,
  deprecatedSettersMessage = "Use copy instead",
  deprecatedSettersSince = "2.1.25"
) case class GraalvmParams(
  defaultVersion: Option[String],
  extraNativeImageOptions: Seq[String]
)
