package coursier.cli.bootstrap

import java.nio.file.{Files, Path, Paths}
import java.util.jar.Attributes

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cache.{ArchiveCache, Cache}
import coursier.jvm.{JvmCache, JvmChannel, JvmIndex}
import coursier.launcher.{MergeRule, ShadingRule}
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
  noMainClass: Boolean,
  createBatFile: Boolean,
  assemblyRules: Seq[MergeRule],
  shadingRules: Seq[ShadingRule],
  baseManifestOpt: Option[Array[Byte]],
  preambleOpt: Option[Boolean],
  deterministicOutput: Boolean,
  proguarded: Boolean,
  hybrid: Boolean,
  nativeImage: Boolean,
  nativeImageIntermediateAssembly: Boolean,
  graalvmVersionOpt: Option[String],
  graalvmJvmOptions: Seq[String],
  graalvmOptions: Seq[String],
  disableJarCheckingOpt: Option[Boolean],
  jvmIndexUrlOpt: Option[String],
  validateChecksums: Boolean = false,
  manifestAttributes: Seq[(String, String)] = Nil
) {
  import BootstrapSpecificParams.BootstrapPackaging
  def batOutput: Path =
    output.getParent.resolve(output.getFileName.toString + ".bat")
  def bootstrapPackaging: BootstrapPackaging =
    BootstrapPackaging(
      standalone,
      hybrid,
      embedFiles,
      validateChecksums
    )

  def jvmCache(cache: Cache[Task]): JvmCache = {
    val archiveCache = ArchiveCache().copy(cache = cache)
    val c = JvmCache()
      .copy(archiveCache = archiveCache)
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

    val validateNoMainClass =
      if (options.noMainClass && !assembly)
        Validated.invalidNel(
          "--no-main-class can only be used along with --assembly (or -a)"
        )
      else
        Validated.validNel(())

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

    val shadingRulesV = options.relocate.traverse { s =>
      val (kind, rest) = {
        val idx = s.indexOf(':')
        if (idx < 0) ("rename", s)
        else (s.substring(0, idx), s.substring(idx + 1))
      }
      val eqIdx = rest.indexOf('=')
      if (eqIdx < 0)
        Validated.invalidNel(
          s"Malformed relocation rule '$s' (expected 'from=to' or 'move-under:from=to')"
        )
      else {
        val from = rest.substring(0, eqIdx).trim
        val to   = rest.substring(eqIdx + 1).trim
        if (from.isEmpty || to.isEmpty)
          Validated.invalidNel(s"Malformed relocation rule '$s' (empty 'from' or 'to')")
        else
          kind match {
            case "rename"     => Validated.validNel(ShadingRule.rename(from, to))
            case "move-under" => Validated.validNel(ShadingRule.moveUnder(from, to))
            case _ =>
              Validated.invalidNel(s"Unrecognized relocation kind '$kind' in rule '$s'")
          }
      }
    }

    val validateRelocate =
      if (options.relocate.nonEmpty && !assembly)
        Validated.invalidNel("--relocate can only be used along with --assembly (or -a)")
      else
        Validated.validNel(())

    val jvmIndex = options
      .jvmIndex
      .map(_.trim)
      .filter(_ != "default")
      .map(JvmChannel.handleAliases)

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

    val manifestAttributesV = {
      val attributesV = options.manifestAttribute.traverse { s =>
        val idx = s.indexOf('=')
        if (idx < 0)
          Validated.invalidNel(s"Malformed manifest attribute '$s' (expected 'name=value')")
        else
          manifestAttribute(s.substring(0, idx).trim, s.substring(idx + 1))
      }
      def packagesV(optionName: String, values: List[String]) =
        values.traverse { s =>
          // like the java option, with the only target a manifest accepts
          val s0       = s.trim.stripSuffix("=ALL-UNNAMED")
          val slashIdx = s0.indexOf('/')
          if (
            slashIdx <= 0 || slashIdx == s0.length - 1 || s0.exists(c => c.isWhitespace || c == '=')
          )
            Validated.invalidNel(
              s"Malformed $optionName value '$s' (expected 'module/package')"
            )
          else
            Validated.validNel(s0)
        }
      (
        attributesV,
        packagesV("--add-exports", options.addExports),
        packagesV("--add-opens", options.addOpens)
      ).tupled.andThen {
        case (attributes, exports, opens) =>
          val duplicates = attributes
            .groupBy { case (name, _) => new Attributes.Name(name) }
            .collect { case (name, values) if values.length > 1 => name.toString }
            .toVector
            .sorted
          // values of --add-exports and --add-opens go after those of --manifest-attribute
          val merged = Seq(addExports -> exports, addOpens -> opens)
            .filter(_._2.nonEmpty)
            .foldLeft(attributes) {
              case (acc, (name, packages)) =>
                val name0 = new Attributes.Name(name)
                acc.find { case (n, _) => new Attributes.Name(n) == name0 } match {
                  case None => acc :+ (name -> packages.mkString(" "))
                  case Some(former @ (formerName, formerValue)) =>
                    acc.filter(_ != former) :+
                      (formerName -> (formerValue +: packages).mkString(" "))
                }
            }
          if (duplicates.nonEmpty)
            Validated.invalidNel(
              s"Manifest attributes passed more than once: ${duplicates.mkString(", ")}"
            )
          else if (merged.nonEmpty && (manifestJar || nativeImage || native))
            Validated.invalidNel(
              "--manifest-attribute, --add-exports, and --add-opens can only be used with " +
                "bootstraps (standalone or not) and assemblies"
            )
          else
            Validated.validNel(merged)
      }
    }

    (
      validateOutputType,
      validateNoMainClass,
      validateRelocate,
      rulesV,
      shadingRulesV,
      baseManifestOptV,
      manifestAttributesV
    ).mapN {
      (_, _, _, rules, shadingRules, baseManifestOpt, manifestAttributes) =>
        BootstrapSpecificParams(
          output,
          options.force,
          standalone,
          options.embedFiles,
          assembly,
          manifestJar,
          options.noMainClass,
          createBatFile,
          prependRules ++ rules,
          shadingRules,
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
          jvmIndex,
          options.validateChecksums,
          manifestAttributes
        )
    }
  }

  private val addExports = "Add-Exports"
  private val addOpens   = "Add-Opens"

  private def manifestAttribute(
    name: String,
    value: String
  ): ValidatedNel[String, (String, String)] =
    scala.util.Try(new Attributes.Name(name)).toOption match {
      case None =>
        Validated.invalidNel(s"Invalid manifest attribute name '$name'")
      case Some(Attributes.Name.MAIN_CLASS) =>
        Validated.invalidNel(
          s"Cannot set ${Attributes.Name.MAIN_CLASS} via --manifest-attribute, use --main-class"
        )
      case Some(_) if value.exists(c => c == '\n' || c == '\r') =>
        Validated.invalidNel(s"Invalid new line in the value of manifest attribute '$name'")
      case Some(_) =>
        Validated.validNel(name -> value)
    }

  final case class BootstrapPackaging(
    standalone: Boolean,
    hybrid: Boolean,
    embedFiles: Boolean,
    validateChecksums: Boolean = false
  )
}
