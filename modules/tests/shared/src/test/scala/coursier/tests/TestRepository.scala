package coursier.tests

import coursier.core._
import coursier.util.{EitherT, Monad}
import coursier.version.{Version => Version0, VersionConstraint => VersionConstraint0}

final case class TestRepository(projects: Map[(Module, VersionConstraint0), Project])
    extends Repository with Repository.VersionApi {

  private lazy val versionsByModule: Map[Module, List[Version0]] =
    projects
      .toList
      .flatMap {
        case ((module, constraint), _) =>
          constraint.preferred.map((module, _))
      }
      .groupBy(_._1)
      .map {
        case (module, l) =>
          (module, l.map(_._2).distinct.sorted)
      }

  override def find0[F[_]](
    module: Module,
    version: Version0,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ) =
    EitherT(
      F.point(
        projects
          .get((module, VersionConstraint0.fromVersion(version)))
          .map((this, _))
          .toRight("Not found")
      )
    )

  /** Lists the versions this repository was built from, so that version-interval constraints (on
    * dependencies or on parent POMs) can be resolved against it.
    */
  override protected def fetchVersions[F[_]](
    module: Module,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Versions, String)] =
    EitherT(
      F.point(
        versionsByModule.get(module).filter(_.nonEmpty) match {
          case None =>
            Left(s"${module.repr} not found")
          case Some(available) =>
            val latest = available.last
            Right((Versions(latest, latest, available, None), s"test:${module.repr}"))
        }
      )
    )

  def artifacts(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[Classifier]]
  ) = ???

}
