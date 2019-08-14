package coursier.cli.native

import java.nio.file.Paths

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import cats.data.{Validated, ValidatedNel}

final case class NativeLauncherOptions(

  @Value("none|boehm|immix|default")
    nativeGc: Option[String] = None,

  @Value("release|debug")
    nativeMode: Option[String] = None,

  nativeLinkStubs: Boolean = true,

  nativeClang: Option[String] = None,

  nativeClangpp: Option[String] = None,

  nativeLinkingOption: List[String] = Nil,
  nativeDefaultLinkingOptions: Boolean = true,
  nativeUseLdflags: Boolean = true,

  nativeCompileOption: List[String] = Nil,
  nativeDefaultCompileOptions: Boolean = true,

  nativeTargetTriple: Option[String] = None,

  nativeLib: Option[String] = None,

  nativeVersion: Option[String] = None,

  @Help("Native compilation target directory")
  @Short("d")
    nativeWorkDir: String = "native-target",
  @Help("Don't wipe native compilation target directory (for debug purposes)")
    nativeKeepWorkDir: Boolean = false

) {

  def params: ValidatedNel[String, NativeLauncherParams] = {

    val gcOpt = nativeGc
      .map(_.trim)
      .filter(_.nonEmpty)

    val modeOpt = nativeMode
      .map(_.trim)
      .filter(_.nonEmpty)

    val linkStubs = nativeLinkStubs
    val clangOpt = nativeClang
      .filter(_.nonEmpty)
      .map(Paths.get(_))
    val clangppOpt = nativeClangpp
      .filter(_.nonEmpty)
      .map(Paths.get(_))

    val prependDefaultLinkingOptions = nativeDefaultLinkingOptions
    val linkingOptions = {
      val ldflags =
        if (nativeUseLdflags) sys.env.get("LDFLAGS").toSeq.flatMap(_.split("\\s+"))
        else Nil
      ldflags ++ nativeLinkingOption
    }

    val prependDefaultCompileOptions = nativeDefaultCompileOptions
    val compileOptions = nativeCompileOption

    val targetTripleOpt = nativeTargetTriple.filter(_.nonEmpty)

    val nativeLibOpt = nativeLib
      .filter(_.nonEmpty)
      .map(Paths.get(_))

    val workDir = Paths.get(nativeWorkDir)
    val keepWorkDir = nativeKeepWorkDir

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
        nativeVersion.map(_.trim).filter(_.nonEmpty)
      )
    )
  }
}

object NativeLauncherOptions {
  implicit val parser = Parser[NativeLauncherOptions]
  implicit val help = caseapp.core.help.Help[NativeLauncherOptions]
}
