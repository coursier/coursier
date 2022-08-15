package coursier.launcher

import java.io.File
import java.nio.file.{Files, Path}

import coursier.launcher.native.NativeBuilder
import coursier.launcher.Parameters.ScalaNative.ScalaNativeOptions

import scala.scalanative.{build => sn}
import scala.scalanative.build.NativeConfig
import scala.scalanative.util.Scope

class NativeBuilderImpl extends NativeBuilder {

  private def ldFlags() =
    Option(System.getenv("LDFLAGS"))
      .toSeq
      .flatMap(_.split("\\s+"))

  def build(
    mainClass: String,
    files: Seq[File],
    output: File,
    options: ScalaNativeOptions,
    log: String => Unit,
    verbosity: Int
  ): Unit = {

    val classpath: Seq[Path] = files.map(_.toPath)
    val outpath              = output.toPath

    val mode = options.modeOpt match {
      case Some("debug")        => sn.Mode.debug
      case Some("release")      => sn.Mode.release
      case Some("release-fast") => sn.Mode.releaseFast
      case Some("release-full") => sn.Mode.releaseFull
      case Some("default")      => sn.Mode.default
      case Some(other)          => throw new Exception(s"Unrecognized native mode '$other'")
      case None                 => sn.Mode.default
    }

    val gc = options.gcOpt match {
      case Some("default") => sn.GC.default
      case Some("none")    => sn.GC.none
      case Some("boehm")   => sn.GC.boehm
      case Some("immix")   => sn.GC.immix
      case Some(other)     => throw new Exception(s"Unrecognized native GC '$other'")
      case None            => sn.GC.default
    }

    val clang = options.clangOpt.getOrElse {
      sn.Discover.clang()
    }
    val clangpp = options.clangppOpt.getOrElse {
      sn.Discover.clangpp()
    }

    val linkingOptions =
      (if (options.prependDefaultLinkingOptions) sn.Discover.linkingOptions() else Nil) ++
        (if (options.prependLdFlags) ldFlags() else Nil) ++
        options.linkingOptions
    val compileOptions =
      (if (options.prependDefaultCompileOptions) sn.Discover.compileOptions() else Nil) ++
        options.compileOptions

    val nativeConfig = sn.NativeConfig.empty
      .withGC(gc)
      .withMode(mode)
      .withLinkStubs(options.linkStubs)
      .withClang(clang)
      .withClangPP(clangpp)
      .withLinkingOptions(linkingOptions)
      .withCompileOptions(compileOptions)
      .withTargetTriple(options.targetTripleOpt)
      .withEmbedResources(true)

    if (mainClass.endsWith("$"))
      ???

    var workDir: Path = null
    try {
      workDir = options.workDirOpt.getOrElse(Files.createTempDirectory("scala-native-"))
      val config = sn.Config.empty
        .withCompilerConfig(nativeConfig)
        .withWorkdir(workDir)
        .withMainClass(mainClass)
        .withClassPath(classpath)
      System.err.println("Class path:")
      for (f <- classpath)
        System.err.println(s"  $f")
      System.err.println(s"Main class: $mainClass")
      Scope { implicit scope =>
        sn.Build.build(config, outpath)
      }
    }
    finally if (!options.keepWorkDir)
      deleteRecursive(workDir.toFile)
  }

  private def deleteRecursive(f: File): Unit = {
    if (f.isDirectory)
      f.listFiles().foreach(deleteRecursive)
    f.delete()
  }
}
