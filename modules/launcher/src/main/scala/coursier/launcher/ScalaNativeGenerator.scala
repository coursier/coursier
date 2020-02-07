package coursier.launcher

import java.nio.file.Path

import coursier.launcher.native.NativeBuilder

object ScalaNativeGenerator extends Generator[Parameters.ScalaNative] {
  def generate(parameters: Parameters.ScalaNative, output: Path): Unit = {
    val builder = NativeBuilder.load(parameters.fetch, parameters.nativeVersion)
    builder.build(
      parameters.mainClass,
      parameters.jars,
      output.toFile,
      parameters.options,
      parameters.log,
      parameters.verbosity
    )
  }
}
