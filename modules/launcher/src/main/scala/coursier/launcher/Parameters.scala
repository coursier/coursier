package coursier.launcher

import java.io.File
import java.nio.file.Path
import java.util.jar.{Attributes => JarAttributes}
import java.util.zip.ZipEntry

import coursier.launcher.internal.Windows
import scala.annotation.unroll

import scala.util.Properties

sealed abstract class Parameters extends Product with Serializable {
  def isNative: Boolean = false
}

object Parameters {

  final case class Assembly(
    files: Seq[File] = Nil,
    mainClass: Option[String] = None,
    attributes: Seq[(JarAttributes.Name, String)] = Nil,
    rules: Seq[MergeRule] = MergeRule.default,
    preambleOpt: Option[Preamble] = Some(Preamble()),
    extraZipEntries: Seq[(ZipEntry, Array[Byte])] = Nil,
    @unroll
    baseManifest: Option[Array[Byte]] = None,
    @unroll
    shadingRules: Seq[ShadingRule] = Nil
  ) extends Parameters {
    def withMainClass(mainClass: String): Assembly =
      copy(mainClass = Some(mainClass))
    def withPreamble(preamble: Preamble): Assembly =
      copy(preambleOpt = Some(preamble))
    def finalAttributes: Seq[(JarAttributes.Name, String)] =
      mainClass
        .map(c => JarAttributes.Name.MAIN_CLASS -> c)
        .toSeq ++
        attributes
  }

  final case class Bootstrap(
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
    @unroll
    python: Boolean = false,
    @unroll
    pythonJep: Boolean = false,
    extraContent: Map[String, Seq[ClassLoaderContent]] = Map(),
    @unroll
    rules: Seq[MergeRule] = MergeRule.default
  ) extends Parameters {

    def withPreamble(preamble: Preamble): Bootstrap =
      copy(preambleOpt = Some(preamble))

    def hasResources: Boolean =
      (content.iterator ++ extraContent.valuesIterator.flatMap(_.iterator)).exists { c =>
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
          p.copy(javaOpts = "-Dsun.misc.URLClassPath.disableJarChecking" +: p.javaOpts)
        }
      else
        preambleOpt

    def addExtraContent(name: String, content: Seq[ClassLoaderContent]): Bootstrap =
      copy(extraContent = extraContent + (name -> content))
  }

  final case class ManifestJar(
    classpath: Seq[File],
    mainClass: String,
    preambleOpt: Option[Preamble] = Some(Preamble())
  ) extends Parameters {

    def withPreamble(preamble: Preamble): ManifestJar =
      copy(preambleOpt = Some(preamble))
  }

  final case class NativeImage(
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
      copy(javaHome = Some(home))
  }

  object NativeImage {
    def defaultGraalvmJvmOptions: Seq[String] =
      Seq("-Xmx3g")
  }

  final case class Prebuilt() extends Parameters {
    override def isNative: Boolean = true
  }

  final case class ScalaNative(
    fetch: Seq[String] => Seq[File],
    mainClass: String,
    nativeVersion: String,
    jars: Seq[File] = Nil,
    options: ScalaNative.ScalaNativeOptions = ScalaNative.ScalaNativeOptions(),
    log: String => Unit = s => System.err.println(s),
    verbosity: Int = 0,
    @unroll
    python: Boolean = false
  ) extends Parameters {
    override def isNative: Boolean = true
  }

  object ScalaNative {

    final case class ScalaNativeOptions(
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
  final case class DummyNative() extends Parameters {
    override def isNative: Boolean = true
  }

}
