package coursier.install

final case class GraalvmParams(
  home: Option[String],
  extraNativeImageOptions: Seq[String]
)
