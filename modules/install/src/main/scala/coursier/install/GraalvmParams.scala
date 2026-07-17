package coursier.install


final case class GraalvmParams(
  defaultVersion: Option[String],
  extraNativeImageOptions: Seq[String]
)
