package coursier.benchmark

import argonaut._, Argonaut._, ArgonautShapeless._
import coursier.core._

object JsonProjCodecs {


  final case class Projectz(
                            module: Module,
                            version: String,
                            // First String is configuration (scope for Maven)
                            dependencies: Seq[(Configuration, Dependency)],
                            // For Maven, this is the standard scopes as an Ivy configuration
                            //configurations: Map[Configuration, Seq[Configuration]],

                            // Maven-specific
                            //parent: Option[(Module, String)],
                            //dependencyManagement: Seq[(Configuration, Dependency)],
                            //properties: Seq[(String, String)],
                            /*profiles: Seq[Profile],
                            versions: Option[Versions],
                            snapshotVersioning: Option[SnapshotVersioning],
                            packagingOpt: Option[Type],
                            relocated: Boolean,

                            /**
                              * Optional exact version used to get this project metadata.
                              * May not match `version` for projects having a wrong version in their metadata.
                              */
                            actualVersionOpt: Option[String],

                            publications: Seq[(Configuration, Publication)],

                            // Extra infos, not used during resolution
                            info: Info*/
                          )

  implicit def confMapEncoder[T: EncodeJson]: EncodeJson[Map[Configuration, T]] =
    EncodeJson.MapEncodeJson[String, T].contramap(_.map { case (k, v) => k.value -> v })
  implicit def confMapDecoder[T: DecodeJson]: DecodeJson[Map[Configuration, T]] =
    DecodeJson.MapDecodeJson[T].map(_.map { case (k, v) => Configuration(k) -> v })

  implicit def seqEncoder[T: EncodeJson]: EncodeJson[Seq[T]] =
    EncodeJson.ListEncodeJson[T].contramap(_.toList)
  implicit def seqDecoder[T: DecodeJson]: DecodeJson[Seq[T]] =
    DecodeJson.ListDecodeJson[T].map(x => x)

  def setEncoder[T: EncodeJson]: EncodeJson[Set[T]] =
    EncodeJson.ListEncodeJson[T].contramap(_.toList)
  def setDecoder[T: DecodeJson]: DecodeJson[Set[T]] =
    DecodeJson.ListDecodeJson[T].map(_.toSet)

  val depEncoder = EncodeJson.of[Dependency]
  val pubEncoder = EncodeJson.of[Publication]
  val verEncoder = EncodeJson.of[Versions]
  val sverEncoder = EncodeJson.of[SnapshotVersioning]
  val infoEncoder = EncodeJson.of[Info]
  val encoder = EncodeJson.of[Project]
  val decoder = DecodeJson.of[Project]

}
