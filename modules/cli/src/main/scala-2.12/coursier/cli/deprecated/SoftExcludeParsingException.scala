package coursier.cli.deprecated

final class SoftExcludeParsingException(
  private val message: String = "",
  private val cause: Throwable = None.orNull
) extends Exception(message, cause)
