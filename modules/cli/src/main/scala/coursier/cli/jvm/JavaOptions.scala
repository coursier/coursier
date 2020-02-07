package coursier.cli.jvm

import caseapp.core.parser.Parser
import caseapp.Recurse

final case class JavaOptions(
  env: Boolean = false,
  @Recurse
    sharedJavaOptions: SharedJavaOptions = SharedJavaOptions()
)

object JavaOptions {
  implicit val parser = Parser[JavaOptions]
  implicit val help = caseapp.core.help.Help[JavaOptions]
}
