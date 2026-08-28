package io.github.alexarchambault.millnativeimage

import java.nio.file as jnio
import mill.api.PathRef

private[millnativeimage] trait NativeImageCompat {

  /**
   * Real on-disk path for `p`. Mill 1.2 serializes `os.Path.toString` / `.toIO`
   * / `.toNIO` as `../mill-workspace/...` aliases that only resolve from a task
   * dest; docker, native-image, and `cmd` need a real path. Symlinks are
   * followed too, since lexically collapsing `../` would otherwise land on
   * Mill's own forwarder symlinks, which don't outlive the session that created
   * them.
   */
  def absPath(p: os.Path): String =
    PathRef.toResolvedPathString(p)

  def absNioPath(p: os.Path): jnio.Path =
    PathRef.toAbsNioPath(PathRef.toResolvedOsPath(p))
}
