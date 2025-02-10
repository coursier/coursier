package coursier.cli.docker

import caseapp.core.RemainingArgs
import coursier.cache.ArchiveCache
import coursier.cli.CoursierCommand
import coursier.docker.{DockerPull, DockerUnpack, DockerUtil, DockerVm}
import coursier.util.Sync

import scala.util.Properties
import coursier.docker.vm.Vm
import coursier.docker.vm.VmFiles

object DockerPullCommand extends CoursierCommand[DockerPullOptions] {
  override def hidden = !experimentalFeatures

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

    if (Properties.isLinux) {
      val priviledgedArchiveCache = ArchiveCache.priviledged()

      val layerDirs = DockerUnpack.unpack(priviledgedArchiveCache, dockerPullResults.layerArtifacts)

      System.err.println(s"${layerDirs.length} unpacked layers")
    }
    else {
      val vmsDir = Vm.defaultVmDir()
      val vm     = Vm.readFrom(vmsDir, params.sharedVmSelectParams.id)
      vm.withSession { session =>
        DockerVm.pullContainer(
          vm.params.hostWorkDir,
          cache,
          dockerPullResults.layerFiles.map(os.Path(_)),
          vm,
          session
        )
      }
    }
  }
}
