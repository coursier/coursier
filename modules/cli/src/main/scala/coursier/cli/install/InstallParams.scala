package coursier.cli.install

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.app.RawAppDescriptor
import coursier.moduleString
import coursier.core.{Module, Repository}
import coursier.parse.{JavaOrScalaModule, ModuleParser, RepositoryParser}

final case class InstallParams(
  shared: SharedInstallParams,
  rawAppDescriptor: RawAppDescriptor,
  channels: Seq[Module],
  repositories: Seq[Repository],
  nameOpt: Option[String]
)

object InstallParams {

  lazy val defaultDir = {
    coursier.paths.CoursierPaths.dataLocalDirectory().toPath.resolve("bin")
  }

  private[install] implicit def validationNelToCats[L, R](v: coursier.util.ValidationNel[L, R]): ValidatedNel[L, R] =
    v.either match {
      case Left(h :: t) => Validated.invalid(NonEmptyList.of(h, t: _*))
      case Right(r) => Validated.validNel(r)
    }

  def apply(options: InstallOptions): ValidatedNel[String, InstallParams] = {

    val sharedV = SharedInstallParams(options.sharedInstallOptions)

    val rawAppDescriptor = options.appOptions.rawAppDescriptor

    val channelsV = Validated.fromEither {
      ModuleParser.javaOrScalaModules(options.channel)
        .either
        .left.map { case h :: t => NonEmptyList.of(h, t: _*) }
        .right.flatMap { modules =>
          modules
            .toList
            .traverse {
              case j: JavaOrScalaModule.JavaModule => Validated.validNel(j.module)
              case s: JavaOrScalaModule.ScalaModule => Validated.invalidNel(s"Scala dependencies ($s) not accepted as channels")
            }
            .toEither
      }
    }

    val defaultChannels =
      if (options.defaultChannels)
        Seq(
          mod"io.get-coursier:apps"
        )
      else Nil

    val repositoriesV = validationNelToCats(RepositoryParser.repositories(options.appOptions.repository))

    val defaultRepositories =
      if (options.defaultRepositories)
        coursier.Resolve.defaultRepositories
      else
        Nil

    val nameOpt = options.name.map(_.trim).filter(_.nonEmpty)

    (sharedV, channelsV, repositoriesV).mapN {
      (shared, channels, repositories) =>
        InstallParams(
          shared,
          rawAppDescriptor,
          channels ++ defaultChannels,
          defaultRepositories ++ repositories,
          nameOpt
        )
    }
  }
}
