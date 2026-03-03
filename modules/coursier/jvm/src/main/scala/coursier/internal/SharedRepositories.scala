package coursier.internal

import coursier.LocalRepositories
import coursier.core.Repository

trait SharedRepositories {
  def central: Repository
  def hardCodedDefaultRepositories: Seq[Repository] = Seq(
    LocalRepositories.ivy2Local,
    central
  )
}
