package coursier
package test

import coursier.core._
import coursier.util.{EitherT, Monad}

final case class TestRepository(projects: Map[(Module, String), Project]) extends Repository {

  def find[F[_]](
    module: Module,
    version: String,
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
