package coursier.cli.jvm

import caseapp._
import coursier.cli.options.OptionGroup

// format: off
final case class SharedJavaOptions(

  @Group(OptionGroup.java)
    jvm: Option[String] = None,

  @Group(OptionGroup.java)
  @Hidden
    systemJvm: Option[Boolean] = None,

  @Group(OptionGroup.java)
  @Hidden
    update: Boolean = false,

  @Group(OptionGroup.java)
    jvmIndex: Option[String] = None
)
// format: on
