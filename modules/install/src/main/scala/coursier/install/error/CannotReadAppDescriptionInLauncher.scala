package coursier.install.error

import java.nio.file.Path

final class CannotReadAppDescriptionInLauncher(val path: Path)
    extends InstallDirException(s"Cannot read app description in $path")
