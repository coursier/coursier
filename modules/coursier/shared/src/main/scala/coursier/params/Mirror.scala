package coursier.params

import coursier.core.Repository

abstract class Mirror extends Serializable {
  def matches(repo: Repository): Option[Repository]
}

object Mirror {
  def replace(repositories: Seq[Repository], mirrors: Seq[Mirror]): Seq[Repository] =
    repositories
      .map { repo =>
        val it = mirrors
          .iterator
          .flatMap(_.matches(repo).iterator)
        if (it.hasNext)
          it.next()
        else
          repo
      }
      .distinct
}
