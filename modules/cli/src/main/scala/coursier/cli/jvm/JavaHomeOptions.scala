package coursier.cli.jvm

import caseapp.core.parser.Parser
import caseapp.Recurse

final case class JavaHomeOptions(
  @Recurse
    sharedJavaOptions: SharedJavaOptions = SharedJavaOptions()
)

object JavaHomeOptions {
  implicit val parser = Parser[JavaHomeOptions]
  implicit val help = caseapp.core.help.Help[JavaHomeOptions]
}
