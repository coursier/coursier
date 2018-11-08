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
  sonatypeOpt: Option[SonatypeParams]
)

object RepositoryParams {

  val sonatypeBase = "https://oss.sonatype.org/"
  val sonatypeReleasesStaging = s"${sonatypeBase}service/local/staging/deploy/maven2"

  val sonatypePublishRepo = PublishRepository(
    MavenRepository("https://oss.sonatype.org/content/repositories/snapshots"),
    None,
    Some(MavenRepository("https://oss.sonatype.org/service/local/staging/deploy/maven2")),
    // is it always releases here? couldn't this depend on the Sonatype profile?
    Some(MavenRepository("https://oss.sonatype.org/content/repositories/releases"))
  )

  def apply(options: RepositoryOptions): ValidatedNel[String, RepositoryParams] = {

    val repositoryV =
      options.repository match {
        case None =>
          if (options.sonatype.contains(true))
            Validated.validNel(sonatypePublishRepo)
          else
            Validated.invalidNel("No repository specified, and --sonatype option not specified")
        case Some(repoUrl) =>
          Parse.repository(repoUrl) match {
            case Left(err) =>
              Validated.invalidNel(err)
            case Right(m: MavenRepository) =>
              Validated.validNel(PublishRepository(m))
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

    val sonatypeRestActions =
      options.sonatype.getOrElse {
        repositoryV.toOption.exists(_.repo.root.startsWith(sonatypeBase))
      }

    val sonatypeParamsOpt =
      if (sonatypeRestActions)
        Some(SonatypeParams(
          restBase = "https://oss.sonatype.org/service/local" // TODO get from options
        ))
      else
        None

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
        if (sonatypeParamsOpt.isEmpty)
          Validated.validNel(None)
        else
          authFromEnv("SONATYPE_USERNAME", "SONATYPE_PASSWORD")
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

    (repositoryV, readRepositoryOptV, credentialsV).mapN {
      (repository, readRepositoryOpt, credentials) =>
        val repo0 = credentials.fold(repository)(repository.withAuthentication)
        val repo = readRepositoryOpt.fold(repo0)(repo0.withReadRepository)
        RepositoryParams(
          repo,
          sonatypeParamsOpt
        )
    }
  }
}
