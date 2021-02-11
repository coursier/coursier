package coursier.cli.jvm

import caseapp.core.parser.Parser
import caseapp.Recurse
import coursier.cli.options.{CacheOptions, EnvOptions, OutputOptions, RepositoryOptions}

final case class JavaOptions(
  installed: Boolean = false,
  available: Boolean = false,
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

object JavaOptions {
  implicit val parser = Parser[JavaOptions]
  implicit val help = caseapp.core.help.Help[JavaOptions]
}
