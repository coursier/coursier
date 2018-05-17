package coursier.cli.options

import caseapp.{ HelpMessage => Help, ValueDescription => Value, ExtraName => Short, _ }

final case class ShoestrapSpecificOptions(
  @Short("M")
  @Short("main")
    mainClass: String = "",
  @Short("o")
    output: String = "shoestrap",
  @Short("f")
    force: Boolean = false,
  @Help("Set Java properties in the generated launcher.")
  @Value("key=value")
  @Short("D")
    property: List[String] = Nil,
  @Help("Set Java command-line options in the generated launcher.")
  @Value("option")
  @Short("J")
    javaOpt: List[String] = Nil,
  @Help("Generate native launcher")
  @Short("S")
    native: Boolean = false,
  @Help("Native compilation target directory")
  @Short("d")
    target: String = "native-target",
  @Help("Don't wipe native compilation target directory (for debug purposes)")
    keepTarget: Boolean = false,
  @Recurse
    isolated: IsolatedLoaderOptions = IsolatedLoaderOptions(),
  @Recurse
    common: CommonOptions = CommonOptions()
)

object ShoestrapSpecificOptions {
  implicit val parser = Parser[ShoestrapSpecificOptions]
  implicit val help = caseapp.core.help.Help[ShoestrapSpecificOptions]
}
