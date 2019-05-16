package coursier.cli.options

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}


final case class SharedLoaderOptions(

  // deprecated, use shared instead
  @Value("target:dependency")
  @Short("I")
  @Short("isolated")
  @Help("(deprecated) dependencies to be put in shared class loaders")
    isolated: List[String] = Nil,

  @Value("dependency[@target]")
  @Help("Dependencies to be put in shared class loaders")
    shared: List[String] = Nil,

  @Help("Comma-separated isolation targets")
  @Short("i")
  @Short("isolateTarget") // former deprecated name
    sharedTarget: List[String] = Nil

)

object SharedLoaderOptions {
  implicit val parser = Parser[SharedLoaderOptions]
  implicit val help = caseapp.core.help.Help[SharedLoaderOptions]
}
