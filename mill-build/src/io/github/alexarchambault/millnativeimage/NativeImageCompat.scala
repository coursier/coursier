package io.github.alexarchambault.millnativeimage

import java.nio.file as jnio
import mill.api.PathRef

private[millnativeimage] trait NativeImageCompat {

  /**
   * Lexically-absolute on-disk path. Mill 1.2 serializes `os.Path.toString` /
   * `.toIO` / `.toNIO` as `../mill-workspace/...` aliases that only resolve
   * from a task dest; docker, native-image, and `cmd` need a real path.
   */
  def absPath(p: os.Path): String =
    PathRef.toAbsString(p)

  def absNioPath(p: os.Path): jnio.Path =
    PathRef.toAbsNioPath(p)
}
