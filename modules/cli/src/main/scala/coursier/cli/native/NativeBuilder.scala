package coursier.cli.native

import java.io.File
import java.net.URLClassLoader

import coursier.core.ModuleName
import coursier.{Dependency, Module, organizationString}
import coursier.util.Task

trait NativeBuilder {
  def build(
    mainClass: String,
    files: Seq[File],
    output0: File,
    params: NativeLauncherParams,
    log: String => Unit = s => Console.err.println(s),
    verbosity: Int = 0
  ): Unit
}

object NativeBuilder {

  def load(
    fetch: coursier.Fetch[Task],
    version: String,
    parentLoader: ClassLoader = Thread.currentThread().getContextClassLoader
  ): NativeBuilder = {

    val sbv = scala.util.Properties.versionNumberString.split('.').take(2).mkString(".")
    val module = Module(org"io.get-coursier", ModuleName(s"coursier-cli-native_${version}_$sbv"))
    val cliOrg = org"io.get-coursier"
    val cliName = ModuleName(s"coursier-cli_$sbv")
    val coursierVersion = coursier.util.Properties.version
    val dep = Dependency(module, coursierVersion, exclusions = Set((cliOrg, cliName)))

    val files = fetch
      .addDependencies(dep)
      .run()

    val loader = new URLClassLoader(files.map(_.toURI.toURL).toArray, parentLoader)

    val cls = loader.loadClass("coursier.cli.native.NativeBuilderImpl")
    cls.newInstance().asInstanceOf[NativeBuilder]
  }

}
