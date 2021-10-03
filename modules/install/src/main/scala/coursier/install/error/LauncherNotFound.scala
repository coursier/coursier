package coursier.install.error

import java.nio.file.Path

final class LauncherNotFound(val path: Path) extends InstallDirException(s"$path not found")
