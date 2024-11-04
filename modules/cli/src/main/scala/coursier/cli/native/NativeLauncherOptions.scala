package coursier.cli.native

import java.nio.file.Paths

import caseapp._
import cats.data.{Validated, ValidatedNel}
import coursier.cli.options.OptionGroup
import coursier.launcher.Parameters.ScalaNative.ScalaNativeOptions

// format: off
final case class NativeLauncherOptions(
  @Group(OptionGroup.native)
  @Hidden
  @ValueDescription("none|boehm|immix|default")
    nativeGc: Option[String] = None,

  @Group(OptionGroup.native)
  @Hidden
  @ValueDescription("release|debug")
    nativeMode: Option[String] = None,

  @Group(OptionGroup.native)
  @Hidden
    nativeLinkStubs: Boolean = true,

  @Group(OptionGroup.native)
  @Hidden
    nativeClang: Option[String] = None,

  @Group(OptionGroup.native)
  @Hidden
    nativeClangpp: Option[String] = None,

  @Group(OptionGroup.native)
  @Hidden
    nativeLinkingOption: List[String] = Nil,
  @Group(OptionGroup.native)
  @Hidden
    nativeDefaultLinkingOptions: Boolean = true,
  @Group(OptionGroup.native)
  @Hidden
    nativeUseLdflags: Boolean = true,

  @Group(OptionGroup.native)
  @Hidden
    nativeCompileOption: List[String] = Nil,
  @Group(OptionGroup.native)
  @Hidden
    nativeDefaultCompileOptions: Boolean = true,

  @Group(OptionGroup.native)
  @Hidden
    nativeTargetTriple: Option[String] = None,

  @Group(OptionGroup.native)
  @Hidden
    nativeLib: Option[String] = None,

  @Group(OptionGroup.native)
  @Hidden
    nativeVersion: Option[String] = None,

  @Group(OptionGroup.native)
  @Hidden
  @HelpMessage("Native compilation target directory")
  @ExtraName("d")
    nativeWorkDir: Option[String] = None,
  @Group(OptionGroup.native)
  @Hidden
  @HelpMessage("Don't wipe native compilation target directory (for debug purposes)")
    nativeKeepWorkDir: Boolean = false

) {
  // format: on

  def params: ValidatedNel[String, ScalaNativeOptions] = {

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
        if (nativeUseLdflags) Option(System.getenv("LDFLAGS")).toSeq.flatMap(_.split("\\s+"))
        else Nil
      ldflags ++ nativeLinkingOption
    }

    val prependDefaultCompileOptions = nativeDefaultCompileOptions
    val compileOptions               = nativeCompileOption

    val targetTripleOpt = nativeTargetTriple.filter(_.nonEmpty)

    val nativeLibOpt = nativeLib
      .filter(_.nonEmpty)
      .map(Paths.get(_))

    val workDirOpt  = nativeWorkDir.map(Paths.get(_))
    val keepWorkDir = nativeKeepWorkDir

    Validated.validNel(
      ScalaNativeOptions()
        .withGcOpt(gcOpt)
        .withModeOpt(modeOpt)
        .withLinkStubs(linkStubs)
        .withClangOpt(clangOpt)
        .withClangppOpt(clangppOpt)
        .withPrependDefaultLinkingOptions(prependDefaultLinkingOptions)
        .withLinkingOptions(linkingOptions)
        .withPrependDefaultCompileOptions(prependDefaultCompileOptions)
        .withCompileOptions(compileOptions)
        .withTargetTripleOpt(targetTripleOpt)
        .withNativeLibOpt(nativeLibOpt)
        .withWorkDirOpt(workDirOpt)
        .withKeepWorkDir(keepWorkDir)
    )
  }
}

object NativeLauncherOptions {
  lazy val parser: Parser[NativeLauncherOptions]                           = Parser.derive
  implicit lazy val parserAux: Parser.Aux[NativeLauncherOptions, parser.D] = parser
  implicit lazy val help: Help[NativeLauncherOptions]                      = Help.derive
}
