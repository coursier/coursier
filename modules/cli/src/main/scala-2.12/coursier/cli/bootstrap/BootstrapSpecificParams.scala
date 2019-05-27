package coursier.cli.bootstrap

import java.nio.file.{Path, Paths}

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.bootstrap.{Assembly, LauncherBat}

final case class BootstrapSpecificParams(
  output: Path,
  force: Boolean,
  standalone: Boolean,
  embedFiles: Boolean,
  javaOptions: Seq[String],
  native: Boolean,
  assembly: Boolean,
  createBatFile: Boolean,
  assemblyRules: Seq[Assembly.Rule],
  withPreamble: Boolean,
  deterministicOutput: Boolean,
  proguarded: Boolean,
  disableJarCheckingOpt: Option[Boolean]
) {
  def batOutput: Path =
    output.getParent.resolve(output.getFileName.toString + ".bat")
}

object BootstrapSpecificParams {
  def apply(options: BootstrapSpecificOptions): ValidatedNel[String, BootstrapSpecificParams] = {

    val validateOutputType = {
      val count = Seq(options.assembly.exists(identity), options.standalone.exists(identity), options.native).count(identity)
      if (count > 1)
        Validated.invalidNel("Only one of --assembly, --standalone, or --native, can be specified")
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

    val createBatFile = options.bat.getOrElse(LauncherBat.isWindows)

    val rulesV = options.assemblyRule.traverse { s =>
      val idx = s.indexOf(':')
      if (idx < 0)
        Validated.invalidNel(s"Malformed assembly rule: $s")
      else {
        val ruleName = s.substring(0, idx)
        val ruleValue = s.substring(idx + 1)
        ruleName match {
          case "append" => Validated.validNel(Assembly.Rule.Append(ruleValue))
          case "append-pattern" => Validated.validNel(Assembly.Rule.AppendPattern(ruleValue))
          case "exclude" => Validated.validNel(Assembly.Rule.Exclude(ruleValue))
          case "exclude-pattern" => Validated.validNel(Assembly.Rule.ExcludePattern(ruleValue))
          case _ => Validated.invalidNel(s"Unrecognized rule name '$ruleName' in rule '$s'")
        }
      }
    }

    val prependRules = if (options.defaultAssemblyRules) Assembly.defaultRules else Nil

    val assembly = options.assembly.getOrElse(false)
    val standalone = options.standalone.getOrElse(false)

    (validateOutputType, rulesV).mapN {
      (_, rules) =>
        val javaOptions = options.javaOpt
        BootstrapSpecificParams(
          output,
          options.force,
          standalone,
          options.embedFiles,
          javaOptions,
          options.native,
          assembly,
          createBatFile,
          prependRules ++ rules,
          options.preamble,
          options.deterministic,
          options.proguarded,
          options.disableJarChecking
        )
    }
  }
}
