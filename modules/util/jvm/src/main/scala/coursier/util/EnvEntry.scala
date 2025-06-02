package coursier.util

/** Environment variable and Java property names
  *
  * The environment variable and the Java property are meant to be equivalent. Down the line, if
  * both the environment variable and the Java property are set, the environment variable takes
  * precedence.
  *
  * @param envName
  *   environent variable name (should be case-sensitive on Unixes, and case-insensitive on Windows)
  * @param propName
  *   Java property name (case-sensitive on all OSes)
  */
final case class EnvEntry(
  envName: String,
  propName: String
) {

  /** Read the environment variable and the Java property from standard locations */
  def read(): EnvValues =
    EnvValues(Option(System.getenv(envName)), sys.props.get(propName))
}
