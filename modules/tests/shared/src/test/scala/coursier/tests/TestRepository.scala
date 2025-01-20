package coursier.tests

import coursier.core._
import coursier.util.{EitherT, Monad}
import coursier.version.VersionConstraint

final case class TestRepository(projects: Map[(Module, VersionConstraint), Project])
    extends Repository {

  def find0[F[_]](
    module: Module,
    version: VersionConstraint,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ) =
    EitherT(
      F.point(
        projects.get((module, version)).map((this, _)).toRight("Not found")
      )
    )

  def artifacts(
    dependency: Dependency,
    project: Project,
    overrideClassifiers: Option[Seq[Classifier]]
  ) = ???

}
