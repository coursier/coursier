package coursier.launcher

import java.nio.file.{Files, Path}

object DummyNativeGenerator extends Generator[Parameters.DummyNative] {
  def generate(parameters: Parameters.DummyNative, output: Path): Unit =
    Files.write(output, Array.emptyByteArray)
}
