package coursier.cli.launch

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cache.{Cache, CacheLogger}
import coursier.cli.install.SharedChannelParams
import coursier.cli.jvm.SharedJavaParams
import coursier.cli.params.SharedLaunchParams
import coursier.env.EnvironmentUpdate
import coursier.launcher.MergeRule
import coursier.util.Task

import java.nio.file.{Path, Paths}

final case class LaunchParams(
  shared: SharedLaunchParams,
  sharedJava: SharedJavaParams,
  channel: SharedChannelParams,
  fork: Boolean,
  jep: Boolean,
  fetchCacheIKnowWhatImDoing: Option[String],
  execve: Option[Boolean],
  hybrid: Boolean,
  useBootstrap: Boolean,
  assemblyRules: Seq[MergeRule],
  workDir: Option[Path]
) {
  def javaPath(cache: Cache[Task]): Task[(String, EnvironmentUpdate)] = {
    val id     = sharedJava.id
    val logger = cache.loggerOpt.getOrElse(CacheLogger.nop)
    for {
      _ <- Task.delay(logger.init())
      (cache0, _) = sharedJava.cacheAndHome(
        cache,
        cache,
        shared.resolve.repositories.repositories,
        shared.resolve.output.verbosity
      )
      handle = coursier.jvm.JavaHome()
        .withCache(cache0)
      javaExe   <- handle.javaBin(id)
      envUpdate <- handle.environmentFor(id)
      _         <- Task.delay(logger.stop()) // FIXME Run even if stuff above fails
    } yield (javaExe.toAbsolutePath.toString, envUpdate)
  }
}

object LaunchParams {
  def apply(options: LaunchOptions): ValidatedNel[String, LaunchParams] = {

    val sharedV     = SharedLaunchParams(options.sharedOptions)
    val sharedJavaV = SharedJavaParams(options.sharedJavaOptions)
    val channelV    = SharedChannelParams(options.channelOptions)

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

    (sharedV, sharedJavaV, channelV, rulesV).mapN { (shared, sharedJava, channel, rules) =>
      val fork: Boolean =
        options.fork.getOrElse(
          options.jep ||
          shared.pythonJep ||
          shared.python ||
          shared.javaOptions.nonEmpty ||
          sharedJava.jvm.nonEmpty ||
          SharedLaunchParams.defaultFork
        )

      LaunchParams(
        shared,
        sharedJava,
        channel,
        fork,
        options.jep,
        options.fetchCacheIKnowWhatImDoing,
        options.execve,
        options.hybrid,
        options.useBootstrap,
        prependRules ++ rules,
        options.workDir
          .filter(_.trim.nonEmpty)
          .map(Paths.get(_))
      )
    }
  }
}
