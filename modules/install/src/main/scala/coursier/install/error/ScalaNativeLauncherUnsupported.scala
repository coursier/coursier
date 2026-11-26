package coursier.install.error

final class ScalaNativeLauncherUnsupported(val appName: String)
    extends InstallDirException(
      "Building Scala Native launchers isn't supported by coursier anymore" +
        (if (appName.isEmpty) "" else s", and no prebuilt binary is available for $appName") +
        ". Use Scala CLI instead, with 'scala-cli --power package --native' " +
        "(see https://scala-cli.virtuslab.org/docs/commands/package)."
    )
