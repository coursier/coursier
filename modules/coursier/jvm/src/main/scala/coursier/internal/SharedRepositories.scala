package coursier.internal

import coursier.core.Repository
import coursier.LocalRepositories

trait SharedRepositories {
  def central: Repository
  def hardCodedDefaultRepositories: Seq[Repository] = Seq(
    LocalRepositories.ivy2Local,
    central
  )
}
