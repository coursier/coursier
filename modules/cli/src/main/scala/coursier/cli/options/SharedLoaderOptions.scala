package coursier.cli.options

import caseapp._
import coursier.install.RawAppDescriptor


// format: off
final case class SharedLoaderOptions(

  // deprecated, use shared instead
  @Group(OptionGroup.launch)
  @Hidden
  @ValueDescription("target:dependency")
  @ExtraName("I")
  @HelpMessage("(deprecated) dependencies to be put in shared class loaders")
    isolated: List[String] = Nil,

  @Group(OptionGroup.launch)
  @Hidden
  @ValueDescription("dependency[@target]")
  @HelpMessage("Dependencies to be put in shared class loaders")
    shared: List[String] = Nil,

  @Group(OptionGroup.launch)
  @Hidden
  @HelpMessage("Comma-separated isolation targets")
  @ExtraName("i")
  @ExtraName("isolateTarget") // former deprecated name
    sharedTarget: List[String] = Nil

) {
  def addApp(app: RawAppDescriptor): SharedLoaderOptions =
    copy(
      shared = {
        val previous = shared
        previous ++ app.shared.filterNot(previous.toSet)
      }
    )
}
// format: on

object SharedLoaderOptions {
  lazy val parser: Parser[SharedLoaderOptions]                           = Parser.derive
  implicit lazy val parserAux: Parser.Aux[SharedLoaderOptions, parser.D] = parser
  implicit lazy val help: Help[SharedLoaderOptions]                      = Help.derive
}
