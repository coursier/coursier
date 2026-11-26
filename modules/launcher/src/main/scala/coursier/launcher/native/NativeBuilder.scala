package coursier.launcher.native

import java.io.File

import coursier.launcher.Parameters.ScalaNative
import coursier.launcher.ScalaNativeLauncherUnsupported

@deprecated(
  "Generating Scala Native launchers isn't supported anymore, use Scala CLI instead",
  "2.1.28"
)
trait NativeBuilder {
  def build(
    mainClass: String,
    files: Seq[File],
    output: File,
    options: ScalaNative.ScalaNativeOptions,
    log: String => Unit = s => Console.err.println(s),
    verbosity: Int = 0
  ): Unit
}

@deprecated(
  "Generating Scala Native launchers isn't supported anymore, use Scala CLI instead",
  "2.1.28"
)
object NativeBuilder {

  def load(
    fetch: Seq[String] => Seq[File],
    version: String,
    parentLoader: ClassLoader = Thread.currentThread().getContextClassLoader
  ): NativeBuilder =
    throw new ScalaNativeLauncherUnsupported

}
