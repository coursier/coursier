package coursier.cli.internal

/** The arguments the launcher was handed.
  *
  * Substituted in Windows native images, where they have to be read back with the code page of the
  * machine running the image rather than the one frozen into it - see `WindowsMainArgs`.
  */
class MainArgs {
  def get(args: Array[String]): Array[String] = args
}
