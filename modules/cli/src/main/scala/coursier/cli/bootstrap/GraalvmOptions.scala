package coursier.cli.bootstrap

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}

// format: off
final case class GraalvmOptions(
  @Group("Graalvm")
  @Hidden // could be visible, but how well does it work?
  @Help("Generate a GraalVM native image")
    nativeImage: Option[Boolean] = None,
  @Group("Graalvm")
  @Hidden
  @Help("When generating a GraalVM native image, merge the classpath into an assembly prior to passing it to native-image")
    intermediateAssembly: Boolean = false,
  @Group("Graalvm")
  @Hidden
  @Help("Use a specific GraalVM version to use to generate native images, such as `22.3`, or `graalvm:21`, or `system`")
  @Short("graalvm")
    graalvmVersion: Option[String] = None,
  @Group("Graalvm")
  @Hidden
  @Short("graalvm-jvm-opt")
    graalvmJvmOption: List[String] = Nil,
  @Group("Graalvm")
  @Hidden
  @Short("graalvm-opt")
    graalvmOption: List[String] = Nil
)
// format: on
