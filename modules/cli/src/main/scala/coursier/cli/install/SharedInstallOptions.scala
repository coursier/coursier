package coursier.cli.install

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}
import coursier.cli.options.OptionGroup

// format: off
final case class SharedInstallOptions(

  @Group(OptionGroup.install)
  @Hidden
    graalvmHome: Option[String] = None,

  @Group(OptionGroup.install)
  @Hidden
    graalvmOption: List[String] = Nil,

  @Group(OptionGroup.install)
  @Hidden
    graalvmDefaultVersion: Option[String] = None,

  @Group(OptionGroup.install)
  @Short("dir")
    installDir: Option[String] = None,

  @Group(OptionGroup.install)
  @Hidden
  @Help("Platform for prebuilt binaries (e.g. \"x86_64-pc-linux\", \"x86_64-apple-darwin\", \"x86_64-pc-win32\")")
    installPlatform: Option[String] = None,

  @Group(OptionGroup.install)
  @Hidden
    installPreferPrebuilt: Boolean = true,

  @Group(OptionGroup.install)
  @Hidden
  @Help("Require prebuilt artifacts for native applications, don't try to build native executable ourselves")
    onlyPrebuilt: Boolean = false,

  @Group(OptionGroup.install)
  @Hidden
    proguarded: Option[Boolean] = None

)
// format: on
