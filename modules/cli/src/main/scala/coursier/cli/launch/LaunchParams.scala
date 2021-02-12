package coursier.cli.launch

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cache.{Cache, CacheLogger}
import coursier.cli.jvm.SharedJavaParams
import coursier.cli.params.SharedLaunchParams
import coursier.env.EnvironmentUpdate
import coursier.util.Task

final case class LaunchParams(
  shared: SharedLaunchParams,
  sharedJava: SharedJavaParams,
  javaOptions: Seq[String],
  jep: Boolean,
  fetchCacheIKnowWhatImDoing: Option[String],
  execve: Option[Boolean]
) {
  lazy val fork: Boolean =
    shared.fork.getOrElse(jep || shared.python || javaOptions.nonEmpty || sharedJava.jvm.nonEmpty || SharedLaunchParams.defaultFork)

  def javaPath(cache: Cache[Task]): Task[(String, EnvironmentUpdate)] = {
    val id = sharedJava.id
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
      javaExe <- handle.javaBin(id)
      envUpdate <- handle.environmentFor(id)
      _ <- Task.delay(logger.stop()) // FIXME Run even if stuff above fails
    } yield (javaExe.toAbsolutePath.toString, envUpdate)
  }
}

object LaunchParams {
  def apply(options: LaunchOptions): ValidatedNel[String, LaunchParams] = {

    val sharedV = SharedLaunchParams(options.sharedOptions)
    val sharedJavaV = SharedJavaParams(options.sharedJavaOptions)

    (sharedV, sharedJavaV).mapN { (shared, sharedJava) =>
      LaunchParams(
        shared,
        sharedJava,
        options.javaOpt,
        options.jep,
        options.fetchCacheIKnowWhatImDoing,
        options.execve
      )
    }
  }
}
