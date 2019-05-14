package coursier.cli.params

import java.nio.file.{Path, Paths}
import java.util.Locale

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.options.NativeBootstrapOptions

import scala.scalanative.{build => sn}

final case class NativeBootstrapParams(
  gc: sn.GC,
  mode: sn.Mode,
  linkStubs: Boolean,
  clangOpt: Option[Path],
  clangppOpt: Option[Path],
  prependDefaultLinkingOptions: Boolean,
  linkingOptions: Seq[String],
  prependDefaultCompileOptions: Boolean,
  compileOptions: Seq[String],
  targetTripleOpt: Option[String],
  nativeLibOpt: Option[Path],
  workDir: Path, // tmp dir basically
  keepWorkDir: Boolean
) {
  def clang: Path =
    clangOpt.getOrElse {
      sn.Discover.clang()
    }
  def clangpp: Path =
    clangppOpt.getOrElse {
      sn.Discover.clangpp()
    }

  def finalLinkingOptions: Seq[String] =
    (if (prependDefaultLinkingOptions) sn.Discover.linkingOptions() else Nil) ++
      linkingOptions
  def finalCompileOptions: Seq[String] =
    (if (prependDefaultCompileOptions) sn.Discover.compileOptions() else Nil) ++
      compileOptions
}

object NativeBootstrapParams {
  def apply(options: NativeBootstrapOptions): ValidatedNel[String, NativeBootstrapParams] = {

    val gcV = options
      .nativeGc
      .map(_.trim)
      .filter(_.nonEmpty)
      .fold(Validated.validNel[String, sn.GC](sn.GC.default)) { s =>
        s.toLowerCase(Locale.ROOT) match {
          case "none" => Validated.validNel(sn.GC.none)
          case "boehm" => Validated.validNel(sn.GC.boehm)
          case "immix" => Validated.validNel(sn.GC.immix)
          case "default" => Validated.validNel(sn.GC.default)
          case _ => Validated.invalidNel(s"Unrecognized scala-native GC: '$s'")
        }
      }

    val modeV = options
      .nativeMode
      .map(_.trim)
      .filter(_.nonEmpty)
      .fold(Validated.validNel[String, sn.Mode](sn.Mode.default)) { s =>
        s.toLowerCase(Locale.ROOT) match {
          case "debug" => Validated.validNel(sn.Mode.debug)
          case "release" => Validated.validNel(sn.Mode.release)
          case "default" => Validated.validNel(sn.Mode.default)
          case _ => Validated.invalidNel(s"Unrecognized scala-native mode: '$s'")
        }
      }

    val linkStubs = options.nativeLinkStubs
    val clangOpt = options.nativeClang
      .filter(_.nonEmpty)
      .map(Paths.get(_))
    val clangppOpt = options
      .nativeClangpp
      .filter(_.nonEmpty)
      .map(Paths.get(_))

    val prependDefaultLinkingOptions = options.nativeDefaultLinkingOptions
    val linkingOptions = {
      val ldflags =
        if (options.nativeUseLdflags) sys.env.get("LDFLAGS").toSeq.flatMap(_.split("\\s+"))
        else Nil
      ldflags ++ options.nativeLinkingOption
    }

    val prependDefaultCompileOptions = options.nativeDefaultCompileOptions
    val compileOptions = options.nativeCompileOption

    val targetTripleOpt = options
      .nativeTargetTriple
      .filter(_.nonEmpty)

    val nativeLibOpt = options
      .nativeLib
      .filter(_.nonEmpty)
      .map(Paths.get(_))

    val workDir = Paths.get(options.nativeWorkDir)
    val keepWorkDir = options.nativeKeepWorkDir

    (gcV, modeV).mapN {
      (gc, mode) =>
        NativeBootstrapParams(
          gc,
          mode,
          linkStubs,
          clangOpt,
          clangppOpt,
          prependDefaultLinkingOptions,
          linkingOptions,
          prependDefaultCompileOptions,
          compileOptions,
          targetTripleOpt,
          nativeLibOpt,
          workDir,
          keepWorkDir
        )
    }
  }
}
