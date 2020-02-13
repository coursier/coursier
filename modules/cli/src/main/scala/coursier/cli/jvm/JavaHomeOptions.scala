package coursier.cli.jvm

import coursier.cli.options.{CacheOptions, OutputOptions}
import caseapp.core.parser.Parser
import caseapp.Recurse

final case class JavaHomeOptions(
  @Recurse
    sharedJavaOptions: SharedJavaOptions = SharedJavaOptions(),
  @Recurse
    cacheOptions: CacheOptions = CacheOptions(),
  @Recurse
    outputOptions: OutputOptions = OutputOptions(),
  setup: Boolean = false,
  userHome: Option[String] = None
)

object JavaHomeOptions {
  implicit val parser = Parser[JavaHomeOptions]
  implicit val help = caseapp.core.help.Help[JavaHomeOptions]
}
