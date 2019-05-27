package coursier.cli.install

final case class GraalvmParams(
  home: String,
  extraNativeImageOptions: Seq[String]
)
