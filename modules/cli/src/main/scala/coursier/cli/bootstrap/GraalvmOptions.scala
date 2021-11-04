package coursier.cli.bootstrap

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}

// format: off
final case class GraalvmOptions(
  @Group("Graalvm")
  @Help("Generate a GraalVM native image")
    nativeImage: Option[Boolean] = None,
  @Group("Graalvm")
  @Help("When generating a GraalVM native image, merge the classpath into an assembly prior to passing it to native-image")
    intermediateAssembly: Boolean = false,
  @Group("Graalvm")
  @Help("GraalVM version to use to generate native images")
  @Short("graalvm")
    graalvmVersion: Option[String] = None,
  @Group("Graalvm")
  @Short("graalvm-jvm-opt")
    graalvmJvmOption: List[String] = Nil,
  @Group("Graalvm")
  @Short("graalvm-opt")
    graalvmOption: List[String] = Nil
)
// format: on
