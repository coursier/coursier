package coursier.cli.publish.params

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.publish.PublishRepository
import coursier.cli.publish.options.RepositoryOptions
import coursier.core.Authentication
import coursier.maven.MavenRepository
import coursier.parse.RepositoryParser

final case class RepositoryParams(
  repository: PublishRepository,
  snapshotVersioning: Boolean,
  gitHub: Boolean,
  bintray: Boolean,
  bintrayLicenses: Seq[String],
  bintrayVcsUrlOpt: Option[String]
)

object RepositoryParams {

  val sonatypeBase = "https://oss.sonatype.org"

  def apply(options: RepositoryOptions): ValidatedNel[String, RepositoryParams] = {

    // FIXME Take repo from conf file into account here
    val sonatype = options.sonatype
      .getOrElse(options.repository.isEmpty && options.github.isEmpty && options.bintray.isEmpty) // or .getOrElse(false)?

    def defaultRepositoryV= {
      val repositoryV =
        options.repository match {
          case None =>
            Validated.invalidNel("No repository specified, and --sonatype option not specified")
          case Some(repoUrl) =>
            RepositoryParser.repository(repoUrl, maybeFile = true) match {
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
            RepositoryParser.repository(repoUrl, maybeFile = true) match {
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

    def fromGitHub(ghCredentials: String): ValidatedNel[String, PublishRepository] = {

      val (ghUser, ghTokenOpt) =
        ghCredentials.split(":", 2) match {
          case Array(user) => (user, None)
          case Array(user, token) => (user, Some(token))
        }

      val ghTokenV =
        ghTokenOpt match {
          case Some(token) => Validated.validNel(token)
          case None =>
            sys.env.get("GH_TOKEN") match {
              case Some(token) => Validated.validNel(token)
              case None => Validated.invalidNel("No GitHub token specified")
            }
        }

      ghTokenV.map { token =>
        PublishRepository.gitHub(ghUser, token)
      }
    }

    def fromBintray(repo: String, apiKey: Option[String]): ValidatedNel[String, PublishRepository] = {

      val paramsV = repo.split("/", 3) match {
        case Array(user, repo0, package0) =>
          Validated.validNel((user, repo0, package0))
        case Array(user, repo0) =>
          Validated.validNel((user, repo0, "default"))
        case Array(user) =>
          Validated.validNel((user, "maven", "default"))
        case _ =>
          Validated.invalidNel(s"Invalid bintray repository: '$repo' (expected 'user/repository/package')")
      }

      val apiKeyV = apiKey match {
        case None => Validated.invalidNel("No Bintray API key specified (--bintray-api-key or BINTRAY_API_KEY in the environment)")
        case Some(key) => Validated.validNel(key)
      }

      (paramsV, apiKeyV).mapN {
        case ((user, repo0, package0), key) =>
          PublishRepository.bintray(user, repo0, package0, key)
      }
    }

    def fromSonatype =
      if (options.repository.nonEmpty || options.readFrom.nonEmpty)
        Validated.invalidNel("Cannot specify --repository or --read-fron along with --sonatype")
      else
        Validated.validNel(
          PublishRepository.Sonatype(MavenRepository(sonatypeBase))
        )

    val repositoryV =
      options.github.map(fromGitHub)
        .orElse(options.bintray.map(fromBintray(_, options.bintrayApiKey.orElse(sys.env.get("BINTRAY_API_KEY")))))
        .getOrElse {
          if (sonatype)
            fromSonatype
          else
            defaultRepositoryV
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
        if (sonatype)
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
          snapshotVersioning,
          options.github.nonEmpty,
          options.bintray.nonEmpty,
          options.bintrayLicense,
          options.bintrayVcsUrl
        )
    }
  }
}
