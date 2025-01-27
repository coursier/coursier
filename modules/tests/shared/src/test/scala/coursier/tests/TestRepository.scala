package coursier.tests

import coursier.core._
import coursier.util.{EitherT, Monad}
import coursier.version.{Version => Version0, VersionConstraint => VersionConstraint0}

final case class TestRepository(projects: Map[(Module, VersionConstraint0), Project])
    extends Repository with Repository.VersionApi {

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

  def artifacts(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[Classifier]]
  ) = ???

}
