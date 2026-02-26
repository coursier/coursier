package coursier.launcher

import ai.kien.python.Python
import coursier.launcher.native.NativeBuilder

import java.nio.file.Path

object ScalaNativeGenerator extends Generator[Parameters.ScalaNative] {
  def generate(parameters: Parameters.ScalaNative, output: Path): Unit = {
    val options =
      if (parameters.python) {
        val extraLdflags = Python().ldflags.get
        parameters.options.withLinkingOptions(
          parameters.options.linkingOptions ++ extraLdflags
        )
      }
      else
        parameters.options

    val builder = NativeBuilder.load(parameters.fetch, parameters.nativeVersion)
    builder.build(
      parameters.mainClass,
      parameters.jars,
      output.toFile,
      options,
      parameters.log,
      parameters.verbosity
    )
  }
}
