package coursier.cli.jvm

import caseapp.core.parser.Parser
import caseapp.Recurse
import coursier.cli.options.{CacheOptions, OutputOptions}

final case class JavaOptions(
  env: Boolean = false,
  installed: Boolean = false,
  available: Boolean = false,
  @Recurse
    sharedJavaOptions: SharedJavaOptions = SharedJavaOptions(),
  @Recurse
    cacheOptions: CacheOptions = CacheOptions(),
  @Recurse
    outputOptions: OutputOptions = OutputOptions()
)

object JavaOptions {
  implicit val parser = Parser[JavaOptions]
  implicit val help = caseapp.core.help.Help[JavaOptions]
}
