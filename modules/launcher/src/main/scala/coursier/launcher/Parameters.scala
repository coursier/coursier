package coursier.launcher

import java.io.File
import java.nio.file.{Path, Paths}
import java.util.jar.{Attributes => JarAttributes}
import java.util.zip.ZipEntry

import coursier.launcher.internal.Windows
import dataclass._

import scala.util.Properties

sealed abstract class Parameters extends Product with Serializable {
  def isNative: Boolean = false
}

object Parameters {

  @data class Assembly(
    files: Seq[File] = Nil,
    mainClass: Option[String] = None,
    attributes: Seq[(JarAttributes.Name, String)] = Nil,
    rules: Seq[MergeRule] = MergeRule.default,
    preambleOpt: Option[Preamble] = Some(Preamble()),
    extraZipEntries: Seq[(ZipEntry, Array[Byte])] = Nil,
    @since("2.0.0-RC6-27")
    baseManifest: Option[Array[Byte]] = None
  ) extends Parameters {
    def withMainClass(mainClass: String): Assembly =
      withMainClass(Some(mainClass))
    def withPreamble(preamble: Preamble): Assembly =
      withPreambleOpt(Some(preamble))
    def finalAttributes: Seq[(JarAttributes.Name, String)] =
      mainClass
        .map(c => JarAttributes.Name.MAIN_CLASS -> c)
        .toSeq ++
        attributes
  }

  @data class Bootstrap(
    content: Seq[ClassLoaderContent],
    mainClass: String,
    javaProperties: Seq[(String, String)] = Nil,
    bootstrapResourcePathOpt: Option[String] = None,
    deterministic: Boolean = true,
    preambleOpt: Option[Preamble] = Some(Preamble()),
    proguarded: Boolean = true,
    disableJarChecking: Option[Boolean] = None,
    hybridAssembly: Boolean = false,
    extraZipEntries: Seq[(ZipEntry, Array[Byte])] = Nil,
    @since("2.0.4")
    python: Boolean = false
  ) extends Parameters {

    def withPreamble(preamble: Preamble): Bootstrap =
      withPreambleOpt(Some(preamble))

    def hasResources: Boolean =
      content.exists { c =>
        c.entries.exists {
          case _: ClassPathEntry.Resource => true
          case _                          => false
        }
      }

    def finalDisableJarChecking: Boolean =
      disableJarChecking.getOrElse(hasResources)

    def finalPreambleOpt: Option[Preamble] =
      if (finalDisableJarChecking)
        preambleOpt.map { p =>
          p.withJavaOpts("-Dsun.misc.URLClassPath.disableJarChecking" +: p.javaOpts)
        }
      else
        preambleOpt
  }

  @data class ManifestJar(
    classpath: Seq[File],
    mainClass: String,
    preambleOpt: Option[Preamble] = Some(Preamble())
  ) extends Parameters {

    def withPreamble(preamble: Preamble): ManifestJar =
      withPreambleOpt(Some(preamble))
  }

  @data class NativeImage(
    mainClass: String,
    fetch: Seq[String] => Seq[File],
    jars: Seq[File] = Nil,
    graalvmVersion: Option[String] = None,
    graalvmJvmOptions: Seq[String] = NativeImage.defaultGraalvmJvmOptions,
    graalvmOptions: Seq[String] = Nil,
    javaHome: Option[File] = None, // needs a "JVMCI-enabled JDK" (like GraalVM)
    nameOpt: Option[String] = None,
    verbosity: Int = 0,
    intermediateAssembly: Boolean = false,
    windowsPathExtensions: Option[Seq[String]] =
      if (Properties.isWin) Some(Windows.pathExtensions) else None,
    isWindows: Boolean = Properties.isWin
  ) extends Parameters {
    override def isNative: Boolean = true
    def withJavaHome(home: File): NativeImage =
      withJavaHome(Some(home))
  }

  object NativeImage {
    def defaultGraalvmJvmOptions: Seq[String] =
      Seq("-Xmx3g")
  }

  @data class Prebuilt() extends Parameters {
    override def isNative: Boolean = true
  }

  @data class ScalaNative(
    fetch: Seq[String] => Seq[File],
    mainClass: String,
    nativeVersion: String,
    jars: Seq[File] = Nil,
    options: ScalaNative.ScalaNativeOptions = ScalaNative.ScalaNativeOptions(),
    log: String => Unit = s => System.err.println(s),
    verbosity: Int = 0
  ) extends Parameters {
    override def isNative: Boolean = true
  }

  object ScalaNative {

    @data class ScalaNativeOptions(
      gcOpt: Option[String] = None,
      modeOpt: Option[String] = None,
      linkStubs: Boolean = true,
      clangOpt: Option[Path] = None,
      clangppOpt: Option[Path] = None,
      prependDefaultLinkingOptions: Boolean = true,
      linkingOptions: Seq[String] = Nil,
      prependDefaultCompileOptions: Boolean = true,
      prependLdFlags: Boolean = true,
      compileOptions: Seq[String] = Nil,
      targetTripleOpt: Option[String] = None,
      nativeLibOpt: Option[Path] = None,
      workDirOpt: Option[Path] = None,
      keepWorkDir: Boolean = false
    )
  }

  /** For test purposes */
  @data class DummyNative() extends Parameters {
    override def isNative: Boolean = true
  }

}
