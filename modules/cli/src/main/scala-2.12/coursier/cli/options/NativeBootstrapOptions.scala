package coursier.cli.options

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}

final case class NativeBootstrapOptions(

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

  @Help("Native compilation target directory")
  @Short("d")
    nativeWorkDir: String = "native-target",
  @Help("Don't wipe native compilation target directory (for debug purposes)")
    nativeKeepWorkDir: Boolean = false

)

object NativeBootstrapOptions {
  implicit val parser = Parser[NativeBootstrapOptions]
  implicit val help = caseapp.core.help.Help[NativeBootstrapOptions]
}
