package coursier.cli.docker

import caseapp.core.RemainingArgs
import coursier.cli.CoursierCommand
import coursier.docker.vm.Vm

import scala.util.Properties

object VmStop extends CoursierCommand[VmStopOptions] {
  override def hidden = !experimentalFeatures

  override def names = List(
    List("vm", "stop"),
    List("vm-stop")
  )

  def run(options: VmStopOptions, remainingArgs: RemainingArgs): Unit = {
    val params = VmStopParams(options).toEither match {
      case Left(errors) =>
        for (err <- errors.toList)
          System.err.println(err)
        sys.exit(1)
      case Right(params0) => params0
    }

    val vmsDir = Vm.defaultVmDir()

    if (!os.exists(vmsDir / params.vmSelect.id)) {
      System.err.println(s"VM ${params.vmSelect.id} not found")
      sys.exit(
        if (params.fail) 1 else 0
      )
    }

    val vm = Vm.readFrom(vmsDir, params.vmSelect.id)

    val pid = vm.pid()

    def isRunning(): Boolean =
      if (Properties.isWin) {
        val exitCode = os.proc("taskkill", "/FI", s"PID eq $pid")
          .call(stderr = os.Pipe, check = false)
          .exitCode
        exitCode == 0
      }
      else {
        val exitCode = os.proc("kill", "-0", pid)
          .call(stderr = os.Pipe, check = false)
          .exitCode
        exitCode == 0
      }

    if (isRunning()) {
      if (Properties.isWin)
        os.proc("taskkill", "/PID", pid).call()
      else
        os.proc("kill", "-15", pid).call()

      // TODO Check if process keeps running, etc.
    }

    os.remove.all(vmsDir / params.vmSelect.id)
  }
}
