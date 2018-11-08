package coursier.cli.publish.sonatype.params

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.publish.sonatype.options.CreateStagingRepositoryOptions

final case class CreateStagingRepositoryParams(
  raw: Boolean,
  profile: Either[String, String], // left: id, right: name
  description: Option[String],
  sonatype: SonatypeParams
)

object CreateStagingRepositoryParams {
  def apply(options: CreateStagingRepositoryOptions): ValidatedNel[String, CreateStagingRepositoryParams] = {

    val profileV = (options.profileId, options.profile) match {
      case (Some(id), None) =>
        Validated.validNel(Left(id))
      case (None, Some(name)) =>
        Validated.validNel(Right(name))
      case (Some(_), Some(_)) =>
        Validated.invalidNel("Cannot specify both profile id and profile name")
      case (None, None) =>
        Validated.invalidNel("No profile id or profile name specified")
    }

    val description = Some(options.description).filter(_.nonEmpty)

    val sonatypeV = SonatypeParams(options.sonatype)

    (profileV, sonatypeV).mapN {
      (profile, sonatype) =>
        CreateStagingRepositoryParams(
          options.raw,
          profile,
          description,
          sonatype
        )
    }
  }
}
