package coursier.cli.options

import java.nio.file.{Path, Paths}
import java.util.Locale

import caseapp.{ExtraName => Short, HelpMessage => Help, ValueDescription => Value, _}

import scala.scalanative.{build => sn}

final case class NativeBootstrapOptions(

  @Value("none|boehm|immix|default")
    nativeGc: Option[String] = None,

  @Value("release|debug")
    nativeMode: Option[String] = None,

  nativeLinkStubs: Boolean = true,

  nativeClang: Option[String] = None,

  nativeClangpp: Option[String] = None,

  nativeLinkingOption: List[String] = Nil,
  nativeDefaultLinkingOptions: Boolean = true,
  nativeUseLdflags: Boolean = true,

  nativeCompileOption: List[String] = Nil,
  nativeDefaultCompileOptions: Boolean = true,

  nativeTargetTriple: Option[String] = None,

  nativeLib: Option[String] = None

) {

  def gc: sn.GC =
    nativeGc
      .map(_.trim)
      .filter(_.nonEmpty)
      .fold(sn.GC.default) { s =>
        s.toLowerCase(Locale.ROOT) match {
          case "none" => sn.GC.none
          case "boehm" => sn.GC.boehm
          case "immix" => sn.GC.immix
          case "default" => sn.GC.default
          case _ => throw new Exception(s"Unrecognized scala-native GC: '$s'")
        }
      }

  def mode: sn.Mode =
    nativeMode
      .map(_.trim)
      .filter(_.nonEmpty)
      .fold(sn.Mode.default) { s =>
        s.toLowerCase(Locale.ROOT) match {
          case "debug" => sn.Mode.debug
          case "release" => sn.Mode.release
          case "default" => sn.Mode.default
        }
      }

  def clang: Path =
    nativeClang
      .filter(_.nonEmpty)
      .fold(sn.Discover.clang())(Paths.get(_))

  def clangpp: Path =
    nativeClangpp
      .filter(_.nonEmpty)
      .fold(sn.Discover.clangpp())(Paths.get(_))

  def linkingOptions: Seq[String] = {
    val default = if (nativeDefaultLinkingOptions) sn.Discover.linkingOptions() else Nil
    val ldflags =
      if (nativeUseLdflags) sys.env.get("LDFLAGS").toSeq.flatMap(_.split("\\s+"))
      else Nil
    default ++ ldflags ++ nativeLinkingOption
  }

  def compileOptions: Seq[String] = {
    val default = if (nativeDefaultCompileOptions) sn.Discover.compileOptions() else Nil
    default ++ nativeCompileOption
  }

  def targetTriple(workdir: Path, clang: Path = clang): String =
    nativeTargetTriple
      .filter(_.nonEmpty)
      .getOrElse {
        sn.Discover.targetTriple(clang, workdir)
      }

  def nativeLib0(classpath: Seq[Path]): Path =
    nativeLib
      .filter(_.nonEmpty)
      .map(Paths.get(_))
      .getOrElse {
        sn.Discover.nativelib(classpath).get
      }

}
