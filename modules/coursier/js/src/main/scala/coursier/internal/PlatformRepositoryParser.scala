package coursier.internal

import coursier.core.Repository
import coursier.parse.StandardRepository

abstract class PlatformRepositoryParser {

  def repository(input: String): Either[String, Repository] =
    SharedRepositoryParser.repository(input)

  def repositoryAsStandard(input: String): Either[String, StandardRepository] =
    SharedRepositoryParser.repositoryAsStandard(input)

}
