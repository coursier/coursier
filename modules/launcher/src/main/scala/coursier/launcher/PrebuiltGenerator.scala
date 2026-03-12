package coursier.launcher

import java.nio.file.Path

object PrebuiltGenerator extends Generator[Parameters.Prebuilt] {

  def generate(parameters: Parameters.Prebuilt, output: Path): Unit = {
    sys.error("Cannot generate an executable for the 'prebuilt' type")
  }

}
