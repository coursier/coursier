package coursier.params

import coursier.core.Repository

abstract class Mirror extends Serializable {
  def matches(repo: Repository): Option[Repository]
}
