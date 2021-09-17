package coursier.cli.jvm

import coursier.cli.options.{CacheOptions, EnvOptions, OutputOptions, RepositoryOptions}
import caseapp.core.parser.Parser
import caseapp.Recurse

// format: off
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
