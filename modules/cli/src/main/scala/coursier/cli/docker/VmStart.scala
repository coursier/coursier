package coursier.cli.docker

import caseapp.core.RemainingArgs
import coursier.cache.ArchiveCache
import coursier.cli.CoursierCommand
import coursier.docker.vm.{Vm, VmFiles}
import coursier.util.Sync

object VmStart extends CoursierCommand[VmStartOptions] {
  override def hidden = !experimentalFeatures

  override def names = List(
    List("vm", "start"),
    List("vm-start")
  )

  def run(options: VmStartOptions, remainingArgs: RemainingArgs): Unit = {
    val params = VmStartParams(options).toEither match {
      case Left(errors) =>
        for (err <- errors.toList)
          System.err.println(err)
        sys.exit(1)
      case Right(params0) => params0
    }

    val pool         = Sync.fixedThreadPool(params.cache.parallel)
    val logger       = params.output.logger()
    val cache        = params.cache.cache(pool, logger)
    val archiveCache = ArchiveCache().withCache(cache)

    val vmsDir = Vm.defaultVmDir()

    if (os.exists(vmsDir / params.vmSelect.id)) {
      // TODO Check if PID is still running, try to connect via ssh
      System.err.println(s"VM ${params.vmSelect.id} already exists")
      sys.exit(1)
    }

    val vmFiles =
      try logger.using(VmFiles.default(archiveCache = archiveCache)).unsafeRun()(cache.ec)
      catch {
        case ex: Throwable =>
          throw new Exception(ex)
      }

    val vmParams = {
      val baseParams = Vm.Params.default(
        cacheLocation = Some(os.Path(cache.location, os.pwd))
      )
      baseParams.copy(
        memory = params.memory,
        cpu = params.cpu,
        user = params.user,
        useVirtualization = params.virtualization
      )
    }

    val vm = Vm.spawn(
      params.vmSelect.id,
      vmFiles,
      vmParams,
      remainingArgs.all
    )

    vm.withSession { session =>
      for (mount <- vm.params.mounts)
        Vm.runCommand(session)("ls", "-lha", s"/${mount.guestPath}")
    }

    pprint.err.log(vm.pid().toInt)

    vm.persist(vmsDir)
  }
}
