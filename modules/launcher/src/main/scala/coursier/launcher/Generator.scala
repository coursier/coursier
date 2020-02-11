package coursier.launcher

import java.nio.file.Path

abstract class Generator[T <: Parameters] {
  def generate(parameters: T, output: Path): Unit
}

object Generator extends Generator[Parameters] {
  def generate(parameters: Parameters, output: Path): Unit =
    parameters match {
      case a: Parameters.Assembly => AssemblyGenerator.generate(a, output)
      case b: Parameters.Bootstrap => BootstrapGenerator.generate(b, output)
      case n: Parameters.NativeImage => NativeImageGenerator.generate(n, output)
      case s: Parameters.ScalaNative => ScalaNativeGenerator.generate(s, output)
      case d: Parameters.DummyNative => DummyNativeGenerator.generate(d, output)
    }
}
