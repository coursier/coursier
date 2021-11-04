package coursier.cli.native

import java.nio.file.Paths

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import cats.data.{Validated, ValidatedNel}
import coursier.launcher.Parameters.ScalaNative.ScalaNativeOptions

// format: off
final case class NativeLauncherOptions(
  @Group("Native launcher")
  @Value("none|boehm|immix|default")
    nativeGc: Option[String] = None,
  
  @Group("Native launcher")
  @Value("release|debug")
    nativeMode: Option[String] = None,

  @Group("Native launcher")
    nativeLinkStubs: Boolean = true,

  @Group("Native launcher")
    nativeClang: Option[String] = None,

  @Group("Native launcher")
    nativeClangpp: Option[String] = None,

  @Group("Native launcher")
    nativeLinkingOption: List[String] = Nil,
  @Group("Native launcher")
    nativeDefaultLinkingOptions: Boolean = true,
  @Group("Native launcher")
    nativeUseLdflags: Boolean = true,

  @Group("Native launcher")
    nativeCompileOption: List[String] = Nil,
  @Group("Native launcher")
    nativeDefaultCompileOptions: Boolean = true,

  @Group("Native launcher")
    nativeTargetTriple: Option[String] = None,

  @Group("Native launcher")
    nativeLib: Option[String] = None,

  @Group("Native launcher")
    nativeVersion: Option[String] = None,

  @Group("Native launcher")
  @Help("Native compilation target directory")
  @Short("d")
    nativeWorkDir: Option[String] = None,
  @Group("Native launcher")
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
