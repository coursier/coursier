package coursier.cli.docker

import caseapp.core.RemainingArgs
import coursier.cache.ArchiveCache
import coursier.cli.CoursierCommand
import coursier.docker.{DockerPull, DockerUnpack, DockerUtil, Multipass}
import coursier.util.Sync

import scala.util.Properties

object DockerPullCommand extends CoursierCommand[DockerPullOptions] {

  override def names = List(
    List("docker-pull"),
    List("docker", "pull")
  )

  def run(options: DockerPullOptions, args: RemainingArgs): Unit = {
    val params = DockerPullParams(options).toEither match {
      case Left(errors) =>
        for (err <- errors.toList)
          System.err.println(err)
        sys.exit(1)
      case Right(params0) => params0
    }
    val pool   = Sync.fixedThreadPool(params.sharedPullParams.cache.parallel)
    val logger = params.sharedPullParams.output.logger()
    val cache  = params.sharedPullParams.cache.cache(pool, logger)

    val (repoName, repoVersion) = DockerUtil.repoNameVersion(args.all)

    val dockerPullResults = DockerPull.pull(
      repoName,
      repoVersion,
      authRegistry = params.sharedPullParams.authRegistry,
      cache = cache,
      os = params.sharedPullParams.os,
      arch = params.sharedPullParams.cpu,
      archVariant = params.sharedPullParams.cpuVariant
    )

    val workDir = os.temp.dir(prefix = "cs-docker-pull", perms = "rwx------")

    if (Properties.isLinux) {
      val priviledgedArchiveCache = ArchiveCache.priviledged()

      val layerDirs = DockerUnpack.unpack(priviledgedArchiveCache, dockerPullResults.layerArtifacts)

      System.err.println(s"${layerDirs.length} unpacked layers")
    }
    else
      Multipass.pullContainer(
        workDir,
        cache,
        dockerPullResults.layerFiles.map(os.Path(_)),
        "cs-docker-vm"
      )
  }
}
