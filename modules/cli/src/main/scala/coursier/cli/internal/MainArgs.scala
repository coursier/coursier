package coursier.cli.internal

/** The arguments the launcher was handed.
  *
  * Substituted in Windows native images, where they have to be read back off the command line
  * Windows kept, rather than taken from bytes decoded with a charset frozen into the image - see
  * `WindowsMainArgs`.
  */
class MainArgs {
  def get(args: Array[String]): Array[String] = args
}
