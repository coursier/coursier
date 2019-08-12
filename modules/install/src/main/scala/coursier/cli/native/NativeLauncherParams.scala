package coursier.cli.native

import java.nio.file.{Path, Paths}

final case class NativeLauncherParams(
  gcOpt: Option[String],
  modeOpt: Option[String],
  linkStubs: Boolean,
  clangOpt: Option[Path],
  clangppOpt: Option[Path],
  prependDefaultLinkingOptions: Boolean,
  linkingOptions: Seq[String],
  prependDefaultCompileOptions: Boolean,
  compileOptions: Seq[String],
  targetTripleOpt: Option[String],
  nativeLibOpt: Option[Path],
  workDir: Path, // tmp dir basically
  keepWorkDir: Boolean,
  nativeShortVersionOpt: Option[String]
)

object NativeLauncherParams {

  def default(): NativeLauncherParams = {

    val ldflags = sys.env.get("LDFLAGS").toSeq.flatMap(_.split("\\s+"))
    val workDir = Paths.get("native-target")

    NativeLauncherParams(
      None,
      None,
      true,
      None,
      None,
      true,
      ldflags,
      true,
      Nil,
      None,
      None,
      workDir,
      false,
      None
    )
  }
}
