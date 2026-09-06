package coursierbuild

import mill.api.BuildCtx

/** Computes the version of the interface modules.
  *
  * Those have their own versioning scheme, unrelated to the one of the coursier modules, and driven
  * by the `interface-v*` tags of this repository (rather than the `v*` ones).
  */
object InterfaceVersion {

  def tagPrefix = "interface-v"

  /** Version to fall back on when this repository has no `interface-v*` tag at all */
  def noTagVersion = "1.0.29-SNAPSHOT"

  private def gitTag(args: String*): Option[String] = {
    val res = os.proc("git" +: args)
      .call(cwd = BuildCtx.workspaceRoot, stderr = os.Pipe, check = false)
    if (res.exitCode == 0) Some(res.out.trim())
    else None
  }

  /** Version of the next release from `version`, like `1.0.29-M4` -> `1.0.30-SNAPSHOT` */
  def nextSnapshotVersion(version: String): String = {
    val parts = version.split("[.-]").filter(_.nonEmpty)
    if (parts.length < 3 || !parts(2).forall(_.isDigit))
      sys.error(
        s"Cannot compute the version following $version, expected a ${tagPrefix}X.Y.Z-like tag"
      )
    Seq(parts(0), parts(1), (parts(2).toInt + 1).toString).mkString(".") + "-SNAPSHOT"
  }

  def computeBuildVersion(): String = {
    def matching(args: String*) =
      gitTag(("describe" +: args) ++ Seq("--tags", "--match", s"$tagPrefix*", "HEAD") *)
        .map(_.stripPrefix(tagPrefix))
    // right on an interface tag: release version, else the version following the latest tag
    matching("--exact-match")
      .orElse(matching("--abbrev=0").map(nextSnapshotVersion))
      .getOrElse(noTagVersion)
  }

}
