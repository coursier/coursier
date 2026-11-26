package coursier.launcher

import java.nio.file.Path

@deprecated(
  "Generating Scala Native launchers isn't supported anymore, use Scala CLI instead",
  "2.1.28"
)
object ScalaNativeGenerator extends Generator[Parameters.ScalaNative] {
  def generate(parameters: Parameters.ScalaNative, output: Path): Unit =
    throw new ScalaNativeLauncherUnsupported
}
