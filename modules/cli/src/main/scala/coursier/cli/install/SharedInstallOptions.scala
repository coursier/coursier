package coursier.cli.install

import caseapp._
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
    graalvmDefaultVersion: Option[String] = SharedInstallOptions.defaultGraalvmVersion,

  @Group(OptionGroup.install)
  @ExtraName("dir")
    installDir: Option[String] = None,

  @Group(OptionGroup.install)
  @Hidden
  @HelpMessage("Platform for prebuilt binaries (e.g. \"x86_64-pc-linux\", \"x86_64-apple-darwin\", \"x86_64-pc-win32\")")
    installPlatform: Option[String] = None,

  @Group(OptionGroup.install)
  @Hidden
    installPreferPrebuilt: Boolean = true,

  @Group(OptionGroup.install)
  @Hidden
  @HelpMessage("Require prebuilt artifacts for native applications, don't try to build native executable ourselves")
    onlyPrebuilt: Boolean = false,

  @Group(OptionGroup.install)
  @Hidden
    proguarded: Option[Boolean] = None,

  @Group(OptionGroup.install)
  @HelpMessage("Record the SHA-256 of the JARs bootstrap launchers download at run time, and have the launchers check them, downloaded or already in cache, before using them")
    validateChecksums: Boolean = false

)
// format: on

object SharedInstallOptions {
  def defaultGraalvmVersion: Option[String] =
    Some("22.1")
}
