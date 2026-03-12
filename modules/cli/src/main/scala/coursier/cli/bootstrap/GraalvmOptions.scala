package coursier.cli.bootstrap

import caseapp._

// format: off
final case class GraalvmOptions(
  @Group("Graalvm")
  @Hidden // could be visible, but how well does it work?
  @HelpMessage("Generate a GraalVM native image")
    nativeImage: Option[Boolean] = None,
  @Group("Graalvm")
  @Hidden
  @HelpMessage("When generating a GraalVM native image, merge the classpath into an assembly prior to passing it to native-image")
    intermediateAssembly: Boolean = false,
  @Group("Graalvm")
  @Hidden
  @HelpMessage("GraalVM version to use to generate native images")
  @ExtraName("graalvm")
    graalvmVersion: Option[String] = None,
  @Group("Graalvm")
  @Hidden
  @ExtraName("graalvm-jvm-opt")
    graalvmJvmOption: List[String] = Nil,
  @Group("Graalvm")
  @Hidden
  @ExtraName("graalvm-opt")
    graalvmOption: List[String] = Nil
)
// format: on
