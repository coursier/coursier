package coursier.internal

import coursier.core.Repository

trait SharedRepositories {
  def central: Repository
  def hardCodedDefaultRepositories: Seq[Repository] = Seq(
    central
  )
}
