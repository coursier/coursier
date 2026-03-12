package coursier.util

/** Values read in the environment and Java properties
  *
  * The name of the environment variable or of the Java property isn't kept in this class. See
  * [[EnvEntry]] for these.
  *
  * @param env
  *   value read in the environment
  * @param prop
  *   value read in Java properties
  */
final case class EnvValues(env: Option[String], prop: Option[String])
