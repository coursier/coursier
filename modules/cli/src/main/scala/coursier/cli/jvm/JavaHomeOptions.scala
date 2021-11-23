package coursier.cli.jvm

import coursier.cli.options.{CacheOptions, EnvOptions, OutputOptions, RepositoryOptions}
import caseapp.core.parser.Parser
import caseapp.{HelpMessage, Recurse}

// format: off
@HelpMessage(
  "Print the home directory of a particular JVM.\n" +
  "Install the requested JVM if it is not already installed.\n" +
  "\n" +
  "Examples:\n" +
  "$ cs java-home\n" +
  "$ cs java-home --jvm adopt:13.0-2\n" +
  "$ cs java-home --jvm 11\n"
)
final case class JavaHomeOptions(
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

object JavaHomeOptions {
  implicit val parser = Parser[JavaHomeOptions]
  implicit val help   = caseapp.core.help.Help[JavaHomeOptions]
}
