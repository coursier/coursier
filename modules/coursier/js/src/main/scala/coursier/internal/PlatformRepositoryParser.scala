package coursier.internal

import coursier.core.Repository

object PlatformRepositoryParser {

  def repository(input: String): Either[String, Repository] =
    SharedRepositoryParser.repository(input)

}
