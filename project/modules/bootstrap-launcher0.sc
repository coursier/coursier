import $file.^.deps, deps.{Deps, ScalaVersions}
import $file.shared, shared.CsModule

import coursier.launcher.{AssemblyGenerator, ClassPathEntry, Parameters, Preamble}
import mill._, mill.scalalib._
import mill.modules.Jvm

import java.io._
import java.util.zip._

import scala.collection.JavaConverters._
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

  // TODO Factor stuff in the assembly / proguarding code below (use the base assemblies in proguard tasks, …)

  def assembly = T {
    val baseJar    = jar().path
    val cp         = upstreamAssemblyClasspath().toSeq.map(_.path)
    val mainClass0 = mainClass().getOrElse(sys.error("No main class"))

    val dest = T.ctx().dest / "bootstrap-orig.jar"

    val params = Parameters.Assembly()
      .withFiles((baseJar +: cp).map(_.toIO))
      .withMainClass(mainClass0)
      .withPreambleOpt(None)

    AssemblyGenerator.generate(params, dest.toNIO)

    PathRef(dest)
  }

  def proguardedAssembly = T {

    // TODO Cache this more heavily (hash inputs, and don't recompute if inputs didn't change)

    val conf = T.dest / "configuration.pro"
    val dest = T.dest / "proguard-bootstrap.jar"

    val baseJar = jar().path
    val cp      = upstreamAssemblyClasspath().toSeq.map(_.path)
    val q       = "\""
    val classPathConf =
      cp.map(f => s"-injars $q$f$q(!META-INF/MANIFEST.MF)").mkString(System.lineSeparator())
    val sharedConf = sharedProguardConf()

    val confContent =
      s"""-injars "$baseJar"
         |$classPathConf
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
    val cp         = upstreamAssemblyClasspath().toSeq.map(_.path)
    val mainClass0 = resourceAssemblyMainClass()

    val dest = T.ctx().dest / "bootstrap-orig.jar"

    val params = Parameters.Assembly()
      .withFiles((baseJar +: cp).map(_.toIO))
      .withMainClass(mainClass0)
      .withPreambleOpt(None)

    AssemblyGenerator.generate(params, dest.toNIO)

    PathRef(dest)
  }

  def resourceJar = T {
    val baseJar                    = jar().path
    val resourceAssemblyMainClass0 = resourceAssemblyMainClass()
    val dest                       = T.dest / "resource-bootstrap.jar"
    val buf                        = Array.ofDim[Byte](64 * 1024)
    val zf                         = new ZipFile(baseJar.toIO)
    val fos                        = new FileOutputStream(dest.toIO)
    val zos                        = new ZipOutputStream(new BufferedOutputStream(fos))
    var is: InputStream            = null
    for (ent <- zf.entries.asScala) {
      if (ent.getName == "META-INF/MANIFEST.MF") {
        var read = -1
        val baos = new ByteArrayOutputStream
        is = zf.getInputStream(ent)
        while ({
          read = is.read(buf)
          read >= 0
        })
          if (read > 0)
            baos.write(buf, 0, read)
        is.close()
        is = null

        val content  = baos.toByteArray
        val manifest = new java.util.jar.Manifest(new ByteArrayInputStream(content))
        val attr: java.util.jar.Attributes = manifest.getMainAttributes
        attr.put(java.util.jar.Attributes.Name.MAIN_CLASS, resourceAssemblyMainClass0)

        val baos0 = new ByteArrayOutputStream
        manifest.write(baos0)
        val updatedContent = baos0.toByteArray

        val ent0 = new ZipEntry(ent)
        ent0.setSize(updatedContent.length)
        ent0.setCompressedSize(-1L)
        val crc = new CRC32
        crc.update(updatedContent)
        ent0.setCrc(crc.getValue)
        zos.putNextEntry(ent0)
        zos.write(updatedContent)
      }
      else {
        zos.putNextEntry(ent)
        var read = -1
        is = zf.getInputStream(ent)
        while ({
          read = is.read(buf)
          read >= 0
        })
          if (read > 0)
            zos.write(buf, 0, read)
        is.close()
        is = null
      }
      zos.closeEntry()
    }
    zos.finish()
    zos.close()
    fos.close()
    zf.close()
    PathRef(dest)
  }

  def proguardedResourceAssembly = T {
    val conf = T.dest / "configuration.pro"
    val dest = T.dest / "proguard-resource-bootstrap.jar"

    val baseJar = resourceJar().path
    val cp      = upstreamAssemblyClasspath().toSeq.map(_.path)
    val q       = "\""
    val classPathConf =
      cp.map(f => s"-injars $q$f$q(!META-INF/MANIFEST.MF)").mkString(System.lineSeparator())
    val sharedConf = sharedResourceProguardConf()

    val confContent =
      s"""-injars "$baseJar"
         |$classPathConf
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
