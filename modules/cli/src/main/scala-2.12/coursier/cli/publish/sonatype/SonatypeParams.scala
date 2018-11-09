package coursier.cli.publish.sonatype

import caseapp.Tag
import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.core.Authentication

final case class SonatypeParams(
  raw: Boolean,
  listProfiles: Boolean,
  list: Boolean,
  profileIdOpt: Option[String],
  profileNameOpt: Option[String],
  repositoryIdOpt: Option[String],
  create: Boolean,
  close: Boolean,
  promote: Boolean,
  drop: Boolean,
  description: Option[String],
  base: String,
  authentication: Option[Authentication],
  verbosity: Int
) {
  def needListProfiles: Boolean =
    profileIdOpt.isEmpty && (profileNameOpt.nonEmpty || repositoryIdOpt.nonEmpty)
  def needListRepositories: Boolean =
    profileIdOpt.isEmpty && repositoryIdOpt.nonEmpty
}

object SonatypeParams {
  def apply(options: SonatypeOptions): ValidatedNel[String, SonatypeParams] = {

    val description = Some(options.description).filter(_.nonEmpty)

    val checkActionsV =
      if (!options.listProfiles && !options.list && !options.create && !options.close && !options.promote && !options.drop)
        Validated.invalidNel("No action specified (pass either one of --list-profiles, --list, --create, --close, or --promote)")
      else if (options.create && options.profileId.isEmpty && options.profile.isEmpty)
        Validated.invalidNel("Profile id or name required to create a repository")
      else if ((options.close || options.promote || options.drop) && options.repository.isEmpty)
        Validated.invalidNel("Profile id or name or repository id required to close, promote, or drop")
      else
        Validated.validNel(())

    // FIXME this will duplicate error messages (re-uses the same Validated

    val authV = (options.user, options.password) match {
      case (None, None) =>
        (sys.env.get("SONATYPE_USERNAME"), sys.env.get("SONATYPE_PASSWORD")) match {
          case (Some(u), Some(p)) =>
            Validated.validNel(Some(Authentication(u, p)))
          case _ =>
            // should we allow no authentication somehow?
            Validated.invalidNel("No authentication specified (either pass --user and --password, or set SONATYPE_USERNAME and SONATYPE_PASSWORD in the environment)")
        }
      case (Some(u), Some(p)) =>
        Validated.validNel(Some(Authentication(u, p)))
      case (Some(_), None) =>
        Validated.invalidNel("User specified, but no password passed")
      case (None, Some(_)) =>
        Validated.invalidNel("Password specified, but no user passed")
    }

    (checkActionsV, authV).mapN {
      (_, auth) =>
        SonatypeParams(
          options.raw,
          options.listProfiles,
          options.list,
          options.profileId,
          options.profile,
          options.repository,
          options.create,
          options.close,
          options.promote,
          options.drop,
          description,
          options.base.getOrElse("https://oss.sonatype.org/service/local"),
          auth,
          Tag.unwrap(options.verbose)
        )
    }
  }
}
