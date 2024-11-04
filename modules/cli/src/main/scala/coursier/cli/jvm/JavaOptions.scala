package coursier.cli.jvm

import caseapp.core.parser.Parser
import caseapp.{HelpMessage, Recurse}
import coursier.cli.options.{
  CacheOptions,
  EnvOptions,
  OptionGroup,
  OutputOptions,
  RepositoryOptions
}
import caseapp.{Group, Help}

// format: off
@HelpMessage(
  "Manage installed JVMs and run java.\n" +
  "\n" +
  "Examples:\n" +
  "$ cs java --available\n" +
  "$ cs java --installed\n" +
  "$ cs java --jvm adopt:13.0-2 -version\n" +
  "$ cs java --jvm 11 --env\n" +
  "$ cs java --jvm adopt:11 --setup\n"
)
final case class JavaOptions(
  @Group(OptionGroup.java)
  @HelpMessage("List all available JVMs")
    available: Boolean = false,
  @Group(OptionGroup.java)
  @HelpMessage("List all the installed JVMs")
    installed: Boolean = false,
  @Recurse
    sharedJavaOptions: SharedJavaOptions = SharedJavaOptions(),
  @Recurse
    repositoryOptions: RepositoryOptions = RepositoryOptions(),
  @Recurse
    cacheOptions: CacheOptions = CacheOptions(),
  @Recurse
    outputOptions: OutputOptions = OutputOptions(),
  @Recurse
    envOptions: EnvOptions = EnvOptions()
)
// format: on

object JavaOptions {
  implicit lazy val parser: Parser[JavaOptions] = Parser.derive
  implicit lazy val help: Help[JavaOptions]     = Help.derive
}
