package shadingtest.lib

// Test fixture for coursier.launcher.ShadingTests - relocated ("shaded") at test time.
class Greeter {
  def greet(name: String): String =
    s"Hello, $name!"
}
