package coursier.cli.app

final case class GraalvmParams(
  home: String,
  extraNativeImageOptions: Seq[String]
)
