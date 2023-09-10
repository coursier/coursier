package coursier.cli.jvm

import caseapp._
import coursier.cli.options.OptionGroup

// format: off
final case class SharedJavaOptions(

  @Group(OptionGroup.java)
  @HelpMessage("The JVM you're targeting (e.g. --jvm temurin:1.17)")
    jvm: Option[String] = None,

  @Group(OptionGroup.java)
  @Hidden
    systemJvm: Option[Boolean] = None,

  @Group(OptionGroup.java)
  @Hidden
    update: Boolean = false,

  @Group(OptionGroup.java)
  @HelpMessage("Location of the JVM index that you'd like to use")
    jvmIndex: Option[String] = None,

  @Group(OptionGroup.java)
    architecture: Option[String] = None
)
// format: on
