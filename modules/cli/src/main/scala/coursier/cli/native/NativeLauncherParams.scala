package coursier.cli.native

import java.nio.file.{Path, Paths}

import cats.data.{Validated, ValidatedNel}

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
  def apply(options: NativeLauncherOptions): ValidatedNel[String, NativeLauncherParams] = {

    val gcOpt = options
      .nativeGc
      .map(_.trim)
      .filter(_.nonEmpty)

    val modeOpt = options
      .nativeMode
      .map(_.trim)
      .filter(_.nonEmpty)

    val linkStubs = options.nativeLinkStubs
    val clangOpt = options.nativeClang
      .filter(_.nonEmpty)
      .map(Paths.get(_))
    val clangppOpt = options
      .nativeClangpp
      .filter(_.nonEmpty)
      .map(Paths.get(_))

    val prependDefaultLinkingOptions = options.nativeDefaultLinkingOptions
    val linkingOptions = {
      val ldflags =
        if (options.nativeUseLdflags) sys.env.get("LDFLAGS").toSeq.flatMap(_.split("\\s+"))
        else Nil
      ldflags ++ options.nativeLinkingOption
    }

    val prependDefaultCompileOptions = options.nativeDefaultCompileOptions
    val compileOptions = options.nativeCompileOption

    val targetTripleOpt = options
      .nativeTargetTriple
      .filter(_.nonEmpty)

    val nativeLibOpt = options
      .nativeLib
      .filter(_.nonEmpty)
      .map(Paths.get(_))

    val workDir = Paths.get(options.nativeWorkDir)
    val keepWorkDir = options.nativeKeepWorkDir

    Validated.validNel(
      NativeLauncherParams(
        gcOpt,
        modeOpt,
        linkStubs,
        clangOpt,
        clangppOpt,
        prependDefaultLinkingOptions,
        linkingOptions,
        prependDefaultCompileOptions,
        compileOptions,
        targetTripleOpt,
        nativeLibOpt,
        workDir,
        keepWorkDir,
        options.nativeVersion.map(_.trim).filter(_.nonEmpty)
      )
    )
  }
}
