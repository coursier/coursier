package coursier.launcher

import java.io.File
import java.nio.file.{Files, Path}

import coursier.launcher.native.NativeBuilder
import coursier.launcher.Parameters.ScalaNative.ScalaNativeOptions

import scala.scalanative.{build => sn}

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
    val main: String         = mainClass + "$"
    val outpath              = output.toPath

    val mode = options.modeOpt match {
      case Some(mode) => sn.Mode(mode)
      case None => sn.Mode.default
    }

    val gc = options.gcOpt match {
      case Some(gc) => sn.GC(gc)
      case None => sn.GC.default
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

    val config = sn.Config.empty
      .withGC(gc)
      .withMode(mode)
      .withLinkStubs(options.linkStubs)
      .withClang(clang)
      .withClangPP(clangpp)
      .withLinkingOptions(linkingOptions)
      .withCompileOptions(compileOptions)
      .withNativelib(options.nativeLibOpt.getOrElse(
        sn.Discover.nativelib(files.map(_.toPath)).get
      ))
      .withMainClass(main)
      .withClassPath(classpath)

    var workDir: Path = null
    try {
      workDir = options.workDirOpt.getOrElse(Files.createTempDirectory("scala-native-"))
      val config0 = config
        .withWorkdir(workDir)
        .withTargetTriple(options.targetTripleOpt.getOrElse {
          sn.Discover.targetTriple(clang, workDir)
        })
      sn.Build.build(config0, outpath)
    } finally {
      if (!options.keepWorkDir)
        deleteRecursive(workDir.toFile)
    }
  }

  private def deleteRecursive(f: File): Unit = {
    if (f.isDirectory) {
      f.listFiles().foreach(deleteRecursive)
    }
    f.delete()
  }
}