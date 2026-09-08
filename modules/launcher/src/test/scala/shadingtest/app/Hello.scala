package shadingtest.app

import shadingtest.lib.Greeter

// Test fixture for coursier.launcher.ShadingTests. References Greeter so that we can check that
// references to relocated classes are rewritten too.
object Hello {
  def message(name: String): String =
    new Greeter().greet(name)

  def main(args: Array[String]): Unit =
    println(message(args.headOption.getOrElse("world")))
}
