package coursier.cli.docker

import caseapp.core.RemainingArgs
import coursier.cli.CoursierCommand
import coursier.docker.DockerBuild
import coursier.docker.vm.Vm
import coursier.util.Sync

import scala.util.Properties

object DockerBuildCommand extends CoursierCommand[DockerBuildOptions] {
  override def hidden = !experimentalFeatures

  override def names = List(
    List("docker-build"),
    List("docker", "build")
  )

  def run(options: DockerBuildOptions, args: RemainingArgs): Unit = {

    val rawContextDir = args.all match {
      case Seq() =>
        System.err.println("No docker context (directory) passed")
        sys.exit(1)
      case Seq(arg) =>
        arg
      case other =>
        System.err.println("Too many arguments passed as docker context (directory), expected one")
        sys.exit(1)
    }

    val contextDir = os.Path(rawContextDir, os.pwd)

    if (!os.exists(contextDir)) {
      System.err.println(s"Error: $rawContextDir not found")
      sys.exit(1)
    }
    if (!os.isDir(contextDir)) {
      System.err.println(s"Error: $rawContextDir is not a directory")
      sys.exit(1)
    }

    val params = DockerBuildParams(options).toEither match {
      case Left(errors) =>
        for (err <- errors.toList)
          System.err.println(err)
        sys.exit(1)
      case Right(params0) => params0
    }

    val dockerFile = params.dockerFile.getOrElse {
      contextDir / "Dockerfile"
    }

    if (!os.exists(dockerFile)) {
      System.err.println(s"Error: $dockerFile not found")
      sys.exit(1)
    }
    if (!os.isFile(dockerFile)) {
      System.err.println(s"Error: $dockerFile is not a file")
      sys.exit(1)
    }

    val pool   = Sync.fixedThreadPool(params.sharedPullParams.cache.parallel)
    val logger = params.sharedPullParams.output.logger()
    val cache  = params.sharedPullParams.cache.cache(pool, logger)

    val vmOpt =
      if (Properties.isLinux) None
      else {
        val vmsDir = Vm.defaultVmDir()
        val vm     = Vm.readFrom(vmsDir, params.sharedVmSelectParams.id)
        Some(vm)
      }

    val (config, layers) = DockerBuild.build(
      contextDir,
      Some(dockerFile),
      vmOpt,
      params.sharedPullParams.authRegistry,
      cache,
      os0 = params.sharedPullParams.os,
      arch = params.sharedPullParams.cpu,
      archVariant = params.sharedPullParams.cpuVariant
    )

    pprint.err.log(config)
    pprint.err.log(layers)
  }
}
