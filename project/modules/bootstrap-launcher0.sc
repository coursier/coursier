import $file.^.deps, deps.{Deps, ScalaVersions}
import $file.shared, shared.CsModule

import coursier.launcher.{AssemblyGenerator, ClassPathEntry, Parameters, Preamble}
import mill._, mill.scalalib._
import mill.modules.Jvm

import java.io._
import java.util.zip._

import scala.jdk.CollectionConverters._
import scala.util.Properties.isWin

trait BootstrapLauncher extends CsModule {

  def proguardClassPath: T[Seq[PathRef]]

  def scalaVersion = ScalaVersions.scala213
  def ivyDeps = super.ivyDeps() ++ Seq(
    Deps.jniUtilsBootstrap
  )
  def mainClass = Some("coursier.bootstrap.launcher.Launcher")

  def runtimeLibs = T {
    val javaHome = os.Path(sys.props("java.home"))
    val rtJar    = javaHome / "lib" / "rt.jar"
    val jmods    = javaHome / "jmods"
    if (os.isFile(rtJar))
      PathRef(rtJar)
    else if (os.isDir(jmods))
      PathRef(jmods)
    else
      sys.error(s"$rtJar and $jmods not found")
  }

  def sharedProguardConf = T {
    s"""-libraryjars ${runtimeLibs().path}
       |-dontnote
       |-dontwarn
       |-repackageclasses coursier.bootstrap.launcher
       |-keep class coursier.bootstrap.launcher.jniutils.BootstrapNativeApi {
       |  *;
       |}
       |-keep class coursier.bootstrap.launcher.Launcher {
       |  public static void main(java.lang.String[]);
       |}
       |-keep class coursier.bootstrap.launcher.SharedClassLoader {
       |  public java.lang.String[] getIsolationTargets();
       |}
       |""".stripMargin
  }

  def sharedResourceProguardConf = T {
    s"""-libraryjars ${runtimeLibs().path}
       |-dontnote
       |-dontwarn
       |-repackageclasses coursier.bootstrap.launcher
       |-keep class coursier.bootstrap.launcher.jniutils.BootstrapNativeApi {
       |  *;
       |}
       |-keep class ${resourceAssemblyMainClass()} {
       |  public static void main(java.lang.String[]);
       |}
       |-keep class coursier.bootstrap.launcher.SharedClassLoader {
       |  public java.lang.String[] getIsolationTargets();
       |}
       |""".stripMargin
  }

  def upstreamAssemblyClasspath = T {
    val cp = super.upstreamAssemblyClasspath()
    cp.filter(!_.path.last.startsWith("scala-"))
  }

  private def rules = {
    import java.util.regex.Pattern
    import coursier.launcher.MergeRule._
    Seq(
      ExcludePattern("^" + Pattern.quote("META-INF/services/coursier.jniutils.NativeApi")),
      ExcludePattern("^" + Pattern.quote("META-INF/native-image/") + ".*"),
      ExcludePattern("^" + Pattern.quote("META-INF/MANIFEST.MF") + "$")
    )
  }

  def assembly = T {
    val baseJar    = jar().path
    val cp         = upstreamAssemblyClasspath().iterator.toSeq.map(_.path)
    val mainClass0 = mainClass().getOrElse(sys.error("No main class"))

    val dest = T.dest / "bootstrap-orig.jar"

    val params = Parameters.Assembly()
      .withFiles((baseJar +: cp).map(_.toIO))
      .withMainClass(mainClass0)
      .withPreambleOpt(None)
      .withRules(rules)

    AssemblyGenerator.generate(params, dest.toNIO)

    PathRef(dest)
  }

  def proguardedAssembly = T {

    // TODO Cache this more heavily (hash inputs, and don't recompute if inputs didn't change)

    val conf = T.dest / "configuration.pro"
    val dest = T.dest / "proguard-bootstrap.jar"

    val baseJar    = assembly().path
    val sharedConf = sharedProguardConf()

    val confContent =
      s"""-injars "$baseJar"
         |-outjars "$dest"
         |$sharedConf
         |""".stripMargin
    os.write.over(conf, confContent)

    Jvm.runSubprocess(
      "proguard.ProGuard",
      proguardClassPath().map(_.path),
      Nil,
      Map(),
      Seq("-include", conf.toString)
    )
    PathRef(dest)
  }

  def resourceAssemblyMainClass = T("coursier.bootstrap.launcher.ResourcesLauncher")
  def resourceAssembly = T {
    val baseJar    = jar().path
    val cp         = upstreamAssemblyClasspath().iterator.toSeq.map(_.path)
    val mainClass0 = resourceAssemblyMainClass()

    val dest = T.dest / "bootstrap-orig.jar"

    val params = Parameters.Assembly()
      .withFiles((baseJar +: cp).map(_.toIO))
      .withMainClass(mainClass0)
      .withPreambleOpt(None)
      .withRules(rules)

    AssemblyGenerator.generate(params, dest.toNIO)

    PathRef(dest)
  }

  def proguardedResourceAssembly = T {
    val conf = T.dest / "configuration.pro"
    val dest = T.dest / "proguard-resource-bootstrap.jar"

    val baseJar    = resourceAssembly().path
    val sharedConf = sharedResourceProguardConf()

    val confContent =
      s"""-injars "$baseJar"
         |-outjars "$dest"
         |$sharedConf
         |""".stripMargin
    os.write.over(conf, confContent)

    Jvm.runSubprocess(
      "proguard.ProGuard",
      proguardClassPath().map(_.path),
      Nil,
      Map(),
      Seq("-include", conf.toString)
    )
    PathRef(dest)
  }
}
