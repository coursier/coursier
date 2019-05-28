package coursier.parse

import coursier.core.Repository
import coursier.internal.PlatformRepositoryParser
import coursier.util.ValidationNel
import coursier.util.Traverse.TraverseOps

object RepositoryParser extends PlatformRepositoryParser {

  def repositories(inputs: Seq[String]): ValidationNel[String, Seq[Repository]] =
    inputs
      .toVector
      .validationNelTraverse { s =>
        ValidationNel.fromEither(repository(s))
      }

}
