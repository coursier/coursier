package coursier.cli.native

import caseapp._
import coursier.cli.options.OptionGroup

/** Options of the former Scala Native launcher generation of `cs bootstrap --native`.
  *
  * That feature was removed, these are only accepted so that former command lines get a message
  * pointing users to Scala CLI, rather than an unrecognized-option error.
  */
// format: off
final case class NativeLauncherOptions(
  @Group(OptionGroup.native)
  @Hidden
  @HelpMessage(NativeLauncherOptions.removedHelp)
  @ValueDescription("none|boehm|immix|default")
    nativeGc: Option[String] = None,

  @Group(OptionGroup.native)
  @Hidden
  @HelpMessage(NativeLauncherOptions.removedHelp)
  @ValueDescription("release|debug")
    nativeMode: Option[String] = None,

  @Group(OptionGroup.native)
  @Hidden
  @HelpMessage(NativeLauncherOptions.removedHelp)
    nativeLinkStubs: Boolean = true,

  @Group(OptionGroup.native)
  @Hidden
  @HelpMessage(NativeLauncherOptions.removedHelp)
    nativeClang: Option[String] = None,

  @Group(OptionGroup.native)
  @Hidden
  @HelpMessage(NativeLauncherOptions.removedHelp)
    nativeClangpp: Option[String] = None,

  @Group(OptionGroup.native)
  @Hidden
  @HelpMessage(NativeLauncherOptions.removedHelp)
    nativeLinkingOption: List[String] = Nil,
  @Group(OptionGroup.native)
  @Hidden
  @HelpMessage(NativeLauncherOptions.removedHelp)
    nativeDefaultLinkingOptions: Boolean = true,
  @Group(OptionGroup.native)
  @Hidden
  @HelpMessage(NativeLauncherOptions.removedHelp)
    nativeUseLdflags: Boolean = true,

  @Group(OptionGroup.native)
  @Hidden
  @HelpMessage(NativeLauncherOptions.removedHelp)
    nativeCompileOption: List[String] = Nil,
  @Group(OptionGroup.native)
  @Hidden
  @HelpMessage(NativeLauncherOptions.removedHelp)
    nativeDefaultCompileOptions: Boolean = true,

  @Group(OptionGroup.native)
  @Hidden
  @HelpMessage(NativeLauncherOptions.removedHelp)
    nativeTargetTriple: Option[String] = None,

  @Group(OptionGroup.native)
  @Hidden
  @HelpMessage(NativeLauncherOptions.removedHelp)
    nativeLib: Option[String] = None,

  @Group(OptionGroup.native)
  @Hidden
  @HelpMessage(NativeLauncherOptions.removedHelp)
    nativeVersion: Option[String] = None,

  @Group(OptionGroup.native)
  @Hidden
  @HelpMessage(NativeLauncherOptions.removedHelp)
  @ExtraName("d")
    nativeWorkDir: Option[String] = None,
  @Group(OptionGroup.native)
  @Hidden
  @HelpMessage(NativeLauncherOptions.removedHelp)
    nativeKeepWorkDir: Boolean = false

) derives Parser, Help {
  // format: on

  def isDefault: Boolean =
    this == NativeLauncherOptions()
}

object NativeLauncherOptions {
  final val removedHelp =
    "Removed: generating Scala Native launchers isn't supported anymore, use Scala CLI instead"
}
