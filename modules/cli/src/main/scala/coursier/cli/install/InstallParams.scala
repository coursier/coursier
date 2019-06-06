package coursier.cli.install

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.app.RawAppDescriptor
import coursier.moduleString
import coursier.core.Repository
import coursier.parse.{JavaOrScalaModule, ModuleParser, RepositoryParser}

final case class InstallParams(
  shared: SharedInstallParams,
  rawAppDescriptor: RawAppDescriptor,
  channels: Seq[Channel],
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

    val channelsV = options
      .channel
      .traverse { s =>
        if (s.contains("://"))
          Validated.validNel(Channel.url(s))
        else {
          val e = ModuleParser.javaOrScalaModule(s)
            .right.flatMap {
              case j: JavaOrScalaModule.JavaModule => Right(Channel.module(j.module))
              case s: JavaOrScalaModule.ScalaModule => Left(s"Scala dependencies ($s) not accepted as channels")
            }
            .left.map(NonEmptyList.one)
          Validated.fromEither(e)
        }
      }

    val defaultChannels =
      if (options.defaultChannels)
        Seq(
          Channel.module(mod"io.get-coursier:apps")
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
