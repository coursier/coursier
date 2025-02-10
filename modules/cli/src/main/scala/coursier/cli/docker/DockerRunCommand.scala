package coursier.cli.docker

import caseapp.core.RemainingArgs
import coursier.cache.{ArchiveCache, DigestBasedCache}
import coursier.cli.CoursierCommand
import coursier.docker.{DockerPull, DockerRun, DockerUnpack, DockerUtil}
import coursier.util.Sync
import scala.util.Properties
import coursier.docker.vm.Vm

object DockerRunCommand extends CoursierCommand[DockerRunOptions] {
  override def hidden = !experimentalFeatures

  override def names = List(
    List("docker-run"),
    List("docker", "run")
  )

  def run(options: DockerRunOptions, args: RemainingArgs): Unit = {
    val params = DockerRunParams(options).toEither match {
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

    val vmOpt =
      if (Properties.isLinux) None
      else {
        val vmsDir = Vm.defaultVmDir()
        val vm     = Vm.readFrom(vmsDir, params.sharedVmSelectParams.id)
        Some(vm)
      }

    val res = DockerRun.run(
      cache,
      DigestBasedCache(),
      dockerPullResults.config.config,
      () => dockerPullResults.layerFiles.map(os.Path(_)),
      () => {
        val priviledgedArchiveCache = ArchiveCache.priviledged()
        DockerUnpack.unpack(priviledgedArchiveCache, dockerPullResults.layerArtifacts)
          .map(os.Path(_))
      },
      interactive = params.interactive,
      useExec = params.useExec,
      vmOpt = vmOpt
    )

    pprint.err.log(res.exitCode)

    if (res.exitCode != 0)
      sys.exit(res.exitCode)
  }
}
