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

  def computeBuildVersion(): String = {
    def matching(args: String*) =
      gitTag(("describe" +: args) ++ Seq("--tags", "--match", s"$tagPrefix*", "HEAD") *)
        .map(_.stripPrefix(tagPrefix))
    // right on an interface tag: release version, else the version following the latest tag
    matching("--exact-match")
      .orElse(matching("--abbrev=0").map(SnapshotVersion.next))
      .getOrElse(noTagVersion)
  }

}
