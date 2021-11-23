package coursier.cli.native

import java.nio.file.Paths

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import cats.data.{Validated, ValidatedNel}
import coursier.cli.options.OptionGroup
import coursier.launcher.Parameters.ScalaNative.ScalaNativeOptions

// format: off
final case class NativeLauncherOptions(
  @Group(OptionGroup.native)
  @Hidden
  @Value("none|boehm|immix|default")
    nativeGc: Option[String] = None,
  
  @Group(OptionGroup.native)
  @Hidden
  @Value("release|debug")
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
  @Help("Native compilation target directory")
  @Short("d")
    nativeWorkDir: Option[String] = None,
  @Group(OptionGroup.native)
  @Hidden
  @Help("Don't wipe native compilation target directory (for debug purposes)")
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
  implicit val parser = Parser[NativeLauncherOptions]
  implicit val help   = caseapp.core.help.Help[NativeLauncherOptions]
}
