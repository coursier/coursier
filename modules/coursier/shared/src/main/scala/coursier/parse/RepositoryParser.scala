package coursier.parse

import coursier.core.Repository
import coursier.internal.PlatformRepositoryParser
import coursier.util.ValidationNel
import coursier.util.Traverse.TraverseOps

object RepositoryParser {

  def repository(input: String): Either[String, Repository] =
    PlatformRepositoryParser.repository(input)

  def repositories(inputs: Seq[String]): ValidationNel[String, Seq[Repository]] =
    inputs
      .toVector
      .validationNelTraverse { s =>
        ValidationNel.fromEither(repository(s))
      }

}
