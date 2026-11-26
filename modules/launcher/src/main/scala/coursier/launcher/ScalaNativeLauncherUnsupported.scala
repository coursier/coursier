package coursier.launcher

/** Thrown when asked to generate a Scala Native launcher, which coursier doesn't support anymore */
final class ScalaNativeLauncherUnsupported
    extends Exception(ScalaNativeLauncherUnsupported.message)

object ScalaNativeLauncherUnsupported {
  def message: String =
    "Generating Scala Native launchers isn't supported by coursier anymore. " +
      "Use Scala CLI instead, with 'scala-cli --power package --native' " +
      "(see https://scala-cli.virtuslab.org/docs/commands/package)."
}
