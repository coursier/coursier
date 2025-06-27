package coursier.parse

import coursier.core.Repository
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository

sealed abstract class StandardRepository extends Product with Serializable {
  def repository: Repository
}

object StandardRepository {
  final case class Maven(repository: MavenRepository) extends StandardRepository
  final case class Ivy(repository: IvyRepository)     extends StandardRepository

  object syntax {
    implicit class MavenRepositoryAsStandard(private val repo: MavenRepository) {
      def asStandard: StandardRepository =
        Maven(repo)
    }
    implicit class IvyRepositoryAsStandard(private val repo: IvyRepository) {
      def asStandard: StandardRepository =
        Ivy(repo)
    }
  }
}
