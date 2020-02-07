package coursier.launcher.native

import java.io.File
import java.net.URLClassLoader

import coursier.launcher.Parameters.ScalaNative

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

object NativeBuilder {

  def load(
    fetch: Seq[String] => Seq[File],
    version: String,
    parentLoader: ClassLoader = Thread.currentThread().getContextClassLoader
  ): NativeBuilder = {

    val sbv = scala.util.Properties.versionNumberString.split('.').take(2).mkString(".")
    val coursierVersion = coursier.launcher.internal.Properties.version
    val dep = s"io.get-coursier:coursier-launcher-native_${version}_$sbv:$coursierVersion"

    val files = fetch(Seq(dep))

    val loader = new URLClassLoader(files.map(_.toURI.toURL).toArray, parentLoader)

    val cls = loader.loadClass("coursier.launcher.NativeBuilderImpl")
    cls.newInstance().asInstanceOf[NativeBuilder]
  }

}
