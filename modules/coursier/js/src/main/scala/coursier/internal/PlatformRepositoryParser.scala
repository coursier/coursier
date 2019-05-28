package coursier.internal

import coursier.core.Repository

abstract class PlatformRepositoryParser {

  def repository(input: String): Either[String, Repository] =
    SharedRepositoryParser.repository(input)

}
