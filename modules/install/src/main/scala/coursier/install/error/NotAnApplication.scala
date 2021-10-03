package coursier.install.error

import java.nio.file.Path

final class NotAnApplication(val path: Path)
    extends InstallDirException(s"File $path wasn't installed by cs install")
