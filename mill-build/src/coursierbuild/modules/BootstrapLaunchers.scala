package coursierbuild.modules

import coursierbuild.Deps.{Deps, ScalaVersions}

import coursier.launcher.{AssemblyGenerator, ClassPathEntry, Parameters, Preamble}
import mill._, mill.scalalib._
import mill.util.Jvm

import java.io._
import java.util.zip._

import scala.jdk.CollectionConverters._
import scala.util.Properties.isWin

trait BootstrapLauncher extends CsModule {

  def proguardClassPath: T[Seq[PathRef]]

  def scalaVersion = ScalaVersions.scala213
  def ivyDeps = super.ivyDeps() ++ Seq(
    Deps.directories,
    Deps.jniUtilsBootstrap
  )
  def mainClass = Some("coursier.bootstrap.launcher.Launcher")

  def runtimeLibs = Task {
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

  def sharedProguardConf = Task {
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

  def sharedResourceProguardConf = Task {
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

  def transitiveRunJars: T[Seq[PathRef]] = Task {
    Task.traverse(transitiveModuleDeps)(_.jar)()
  }
  def upstreamAssemblyClasspath: T[Agg[PathRef]] = Task {
    // use JARs instead of directories of upstream deps, to make assembly generation below happy
    val cp = resolvedRunIvyDeps() ++ transitiveRunJars()
    // drop Scala JARs (need refactoring of build so that these are not around here)
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

  def assembly = Task {
    val baseJar    = jar().path
    val cp         = upstreamAssemblyClasspath().iterator.toSeq.map(_.path).filter(os.exists)
    val mainClass0 = mainClass().getOrElse(sys.error("No main class"))

    val dest = Task.dest / "bootstrap-orig.jar"

    val params = Parameters.Assembly()
      .withFiles((baseJar +: cp).map(_.toIO))
      .withMainClass(mainClass0)
      .withPreambleOpt(None)
      .withRules(rules)

    AssemblyGenerator.generate(params, dest.toNIO)

    PathRef(dest)
  }

  def proguardedAssembly = Task {

    // TODO Cache this more heavily (hash inputs, and don't recompute if inputs didn't change)

    val conf = Task.dest / "configuration.pro"
    val dest = Task.dest / "proguard-bootstrap.jar"

    val baseJar    = assembly().path
    val sharedConf = sharedProguardConf()

    val confContent =
      s"""-injars "$baseJar"
         |-outjars "$dest"
         |$sharedConf
         |""".stripMargin
    os.write.over(conf, confContent)

    Jvm.callProcess(
      mainClass = "proguard.ProGuard",
      classPath = proguardClassPath().map(_.path),
      mainArgs = Seq("-include", conf.toString)
    )
    PathRef(dest)
  }

  def resourceAssemblyMainClass = Task("coursier.bootstrap.launcher.ResourcesLauncher")
  def resourceAssembly = Task {
    val baseJar    = jar().path
    val cp         = upstreamAssemblyClasspath().iterator.toSeq.map(_.path)
    val mainClass0 = resourceAssemblyMainClass()

    val dest = Task.dest / "bootstrap-orig.jar"

    val params = Parameters.Assembly()
      .withFiles((baseJar +: cp).map(_.toIO))
      .withMainClass(mainClass0)
      .withPreambleOpt(None)
      .withRules(rules)

    AssemblyGenerator.generate(params, dest.toNIO)

    PathRef(dest)
  }

  def proguardedResourceAssembly = Task {
    val conf = Task.dest / "configuration.pro"
    val dest = Task.dest / "proguard-resource-bootstrap.jar"

    val baseJar    = resourceAssembly().path
    val sharedConf = sharedResourceProguardConf()

    val confContent =
      s"""-injars "$baseJar"
         |-outjars "$dest"
         |$sharedConf
         |""".stripMargin
    os.write.over(conf, confContent)

    Jvm.callProcess(
      mainClass = "proguard.ProGuard",
      classPath = proguardClassPath().map(_.path),
      mainArgs = Seq("-include", conf.toString)
    )
    PathRef(dest)
  }
}
