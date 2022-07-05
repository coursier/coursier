package coursier.cli.bootstrap

import java.nio.file.{Files, Path, Paths}

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cache.{ArchiveCache, Cache}
import coursier.jvm.{JvmCache, JvmIndex}
import coursier.launcher.MergeRule
import coursier.launcher.internal.Windows
import coursier.util.Task

import scala.util.Properties

final case class BootstrapSpecificParams(
  output: Path,
  force: Boolean,
  standalone: Boolean,
  embedFiles: Boolean,
  assembly: Boolean,
  manifestJar: Boolean,
  createBatFile: Boolean,
  assemblyRules: Seq[MergeRule],
  baseManifestOpt: Option[Array[Byte]],
  withPreamble: Boolean,
  deterministicOutput: Boolean,
  proguarded: Boolean,
  hybrid: Boolean,
  nativeImage: Boolean,
  nativeImageIntermediateAssembly: Boolean,
  graalvmVersionOpt: Option[String],
  graalvmJvmOptions: Seq[String],
  graalvmOptions: Seq[String],
  disableJarCheckingOpt: Option[Boolean],
  jvmIndexUrlOpt: Option[String]
) {
  import BootstrapSpecificParams.BootstrapPackaging
  def batOutput: Path =
    output.getParent.resolve(output.getFileName.toString + ".bat")
  def bootstrapPackaging: BootstrapPackaging =
    BootstrapPackaging(
      standalone,
      hybrid,
      embedFiles
    )

  def jvmCache(cache: Cache[Task]): JvmCache = {
    val archiveCache = ArchiveCache().withCache(cache)
    val c = JvmCache()
      .withArchiveCache(archiveCache)
    jvmIndexUrlOpt match {
      case None              => c.withDefaultIndex
      case Some(jvmIndexUrl) => c.withIndex(jvmIndexUrl)
    }
  }
}

object BootstrapSpecificParams {
  def apply(
    options: BootstrapSpecificOptions,
    native: Boolean
  ): ValidatedNel[String, BootstrapSpecificParams] = {
    val graalvmVersion = options.graalvmOptions.graalvmVersion
      .map(_.trim)
      .filter(_.nonEmpty)
      .filter(_ => !options.graalvmOptions.nativeImage.contains(false))

    val (graalvmJvmOptions, graalvmOptions) =
      if (options.graalvmOptions.nativeImage.contains(false))
        (Nil, Nil)
      else
        (
          options.graalvmOptions.graalvmJvmOption.filter(_.nonEmpty),
          options.graalvmOptions.graalvmOption.filter(_.nonEmpty)
        )

    val assembly    = options.assembly.getOrElse(false)
    val manifestJar = options.manifestJar.getOrElse(false)
    val standalone  = options.standalone.getOrElse(false)
    val hybrid      = options.hybrid.getOrElse(false)
    val nativeImage = options.graalvmOptions.nativeImage.getOrElse(graalvmVersion.nonEmpty)

    val validateOutputType = {
      val count = Seq(
        assembly,
        manifestJar,
        standalone,
        hybrid,
        nativeImage,
        native
      ).count(identity)
      if (count > 1)
        Validated.invalidNel(
          "Only one of --assembly (or -a), --manifest-jar, --standalone (or -s), --hybrid, " +
            "--native-image, or --native (or -S), can be specified"
        )
      else
        Validated.validNel(())
    }

    val output = Paths.get {
      options
        .output
        .map(_.trim)
        .filter(_.nonEmpty)
        .getOrElse("bootstrap")
    }.toAbsolutePath

    val createBatFile = options.bat.getOrElse(Properties.isWin)

    val rulesV = options.assemblyRule.traverse { s =>
      val idx = s.indexOf(':')
      if (idx < 0)
        Validated.invalidNel(s"Malformed assembly rule: $s")
      else {
        val ruleName  = s.substring(0, idx)
        val ruleValue = s.substring(idx + 1)
        ruleName match {
          case "append"          => Validated.validNel(MergeRule.Append(ruleValue))
          case "append-pattern"  => Validated.validNel(MergeRule.AppendPattern(ruleValue))
          case "exclude"         => Validated.validNel(MergeRule.Exclude(ruleValue))
          case "exclude-pattern" => Validated.validNel(MergeRule.ExcludePattern(ruleValue))
          case _ => Validated.invalidNel(s"Unrecognized rule name '$ruleName' in rule '$s'")
        }
      }
    }

    val prependRules = if (options.defaultAssemblyRules) MergeRule.default else Nil

    val jvmIndex = options
      .jvmIndex
      .map(_.trim)
      .filter(_ != "default")
      .map(JvmIndex.handleAliases)

    val baseManifestOptV = options.baseManifest.filter(_.nonEmpty) match {
      case None => Validated.validNel(None)
      case Some(path) =>
        val p = Paths.get(path)
        if (Files.isRegularFile(p))
          Validated.validNel(Some(Files.readAllBytes(p)))
        else if (Files.exists(p))
          Validated.invalidNel(s"Base manifest $path is not a file")
        else
          Validated.invalidNel(s"Base manifest $path not found")
    }

    (validateOutputType, rulesV, baseManifestOptV).mapN {
      (_, rules, baseManifestOpt) =>
        BootstrapSpecificParams(
          output,
          options.force,
          standalone,
          options.embedFiles,
          assembly,
          manifestJar,
          createBatFile,
          prependRules ++ rules,
          baseManifestOpt,
          options.preamble,
          options.deterministic,
          options.proguarded,
          hybrid,
          nativeImage,
          options.graalvmOptions.intermediateAssembly,
          graalvmVersion,
          graalvmJvmOptions,
          graalvmOptions,
          options.disableJarChecking,
          jvmIndex
        )
    }
  }

  final case class BootstrapPackaging(
    standalone: Boolean,
    hybrid: Boolean,
    embedFiles: Boolean
  )
}
