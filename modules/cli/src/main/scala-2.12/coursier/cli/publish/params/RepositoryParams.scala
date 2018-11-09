package coursier.cli.publish.params

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.publish.PublishRepository
import coursier.cli.publish.options.RepositoryOptions
import coursier.core.Authentication
import coursier.maven.MavenRepository
import coursier.util.Parse

final case class RepositoryParams(
  repository: PublishRepository,
  snapshotVersioning: Boolean
)

object RepositoryParams {

  val sonatypeBase = "https://oss.sonatype.org"

  def apply(options: RepositoryOptions): ValidatedNel[String, RepositoryParams] = {

    val repositoryV =
      if (options.sonatype.getOrElse(true)) {
        if (options.repository.nonEmpty || options.readFrom.nonEmpty)
          Validated.invalidNel("Cannot specify --repository or --read-fron along with --sonatype")
        else
          Validated.validNel(
            PublishRepository.Sonatype(MavenRepository(sonatypeBase))
          )
      } else {

        val repositoryV =
          options.repository match {
            case None =>
              Validated.invalidNel("No repository specified, and --sonatype option not specified")
            case Some(repoUrl) =>
              Parse.repository(repoUrl) match {
                case Left(err) =>
                  Validated.invalidNel(err)
                case Right(m: MavenRepository) =>
                  Validated.validNel(m)
                case Right(_) =>
                  Validated.invalidNel(s"$repoUrl: non-maven repositories not supported")
              }
          }

        val readRepositoryOptV =
          options.readFrom match {
            case None =>
              Validated.validNel(None)
            case Some(repoUrl) =>
              Parse.repository(repoUrl) match {
                case Left(err) =>
                  Validated.invalidNel(err)
                case Right(m: MavenRepository) =>
                  Validated.validNel(Some(m))
                case Right(_) =>
                  Validated.invalidNel(s"$repoUrl: non-maven repositories not supported")
              }
          }

        (repositoryV, readRepositoryOptV).mapN {
          (repo, readRepoOpt) =>
            PublishRepository.Simple(repo, readRepoOpt)
        }
      }


    def authFromEnv(userVar: String, passVar: String) = {
      val userV = sys.env.get(userVar) match {
        case None => Validated.invalidNel(s"User environment variable $userVar not set")
        case Some(u) => Validated.validNel(u)
      }
      val passV = sys.env.get(passVar) match {
        case None => Validated.invalidNel(s"Password environment variable $passVar not set")
        case Some(u) => Validated.validNel(u)
      }
      (userV, passV).mapN {
        (user, pass) =>
          Some(Authentication(user, pass))
      }
    }

    val credentialsV = options.auth match {
      case None =>
        if (options.sonatype.getOrElse(true))
          authFromEnv("SONATYPE_USERNAME", "SONATYPE_PASSWORD")
        else
          Validated.validNel(None)
      case Some(s) =>

        def handleAuth(auth: String) =
          auth.split(":", 2) match {
            case Array(user, pass) =>
              Validated.validNel(Some(Authentication(user, pass)))
            case _ =>
              Validated.invalidNel("Malformed --auth argument (expected user:password, or env:USER_ENV_VAR:PASSWORD_ENV_VAR)")
          }

        if (s.startsWith("env:")) {
          if (s.contains(":"))
            s.split(":", 2) match {
              case Array(userVar, passVar) =>
                authFromEnv(userVar, passVar)
              case _ =>
                // should not happen
                ???
            }
          else {
            val varName = s.stripPrefix("env:")
            sys.env.get(varName) match {
              case None =>
                Validated.invalidNel(s"Authentication environment variable $varName not set")
              case Some(v) =>
                handleAuth(v)
            }
          }
        } else if (s.startsWith("file:")) {
          // TODO
          ???
        } else
          handleAuth(s)
    }

    val snapshotVersioning = options.snapshotVersioning

    (repositoryV, credentialsV).mapN {
      (repository, credentials) =>
        val repo = credentials.fold(repository)(repository.withAuthentication)
        RepositoryParams(
          repo,
          snapshotVersioning
        )
    }
  }
}
