package coursier.cli.launch

import cats.data.ValidatedNel
import cats.implicits._
import coursier.cache.Cache
import coursier.cli.jvm.SharedJavaParams
import coursier.cli.params.SharedLaunchParams
import coursier.util.Task

final case class LaunchParams(
  shared: SharedLaunchParams,
  sharedJava: SharedJavaParams,
  javaOptions: Seq[String],
  jep: Boolean,
  fetchCacheIKnowWhatImDoing: Option[String]
) {
  lazy val fork: Boolean =
    shared.fork.getOrElse(jep || javaOptions.nonEmpty || sharedJava.jvm.nonEmpty || SharedLaunchParams.defaultFork)

  def javaPath(cache: Cache[Task]): Task[String] =
    sharedJava.jvm match {
      case None => Task.point("java")
      case Some(id) =>
        for {
          baseHandle <- coursier.jvm.JavaHome.default
          handle = baseHandle
            .withJvmCacheLogger(sharedJava.jvmCacheLogger(shared.resolve.output.verbosity))
            .withCoursierCache(cache)
          javaExe <- handle.javaBin(id)
        } yield javaExe.toAbsolutePath.toString
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
        options.fetchCacheIKnowWhatImDoing
      )
    }
  }
}
