package coursierbuild

object SnapshotVersion {

  /** Version of the next release from `version`, like `2.1.25` -> `2.1.26-SNAPSHOT`, or `1.0.29-M4`
    * -> `1.0.30-SNAPSHOT`
    *
    * Keeps the first 3 components of `version`, and increments the last of them.
    */
  def next(version: String): String = {
    val parts = version.split("[.-]").filter(_.nonEmpty)
    if (parts.length < 3 || !parts.take(3).forall(p => p.nonEmpty && p.forall(_.isDigit)))
      sys.error(s"Cannot compute the version following $version, expected an X.Y.Z-like version")
    Seq(parts(0), parts(1), (parts(2).toInt + 1).toString).mkString(".") + "-SNAPSHOT"
  }
}
