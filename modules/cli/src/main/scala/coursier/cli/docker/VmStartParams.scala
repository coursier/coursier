package coursier.cli.docker

import cats.data.ValidatedNel
import cats.syntax.all._
import coursier.cli.params.{CacheParams, OutputParams}
import coursier.docker.vm.Vm

final case class VmStartParams(
  cache: CacheParams,
  output: OutputParams,
  vmSelect: SharedVmSelectParams,
  memory: String,
  cpu: String,
  user: String,
  virtualization: Boolean
)

object VmStartParams {
  def apply(options: VmStartOptions): ValidatedNel[String, VmStartParams] =
    (
      options.cacheOptions.params,
      OutputParams(options.outputOptions),
      SharedVmSelectParams(options.sharedVmSelectOptions)
    ).mapN {
      (cache, output, sharedVmSelect) =>
        VmStartParams(
          cache,
          output,
          sharedVmSelect,
          options.memory.map(_.trim).filter(_.nonEmpty).getOrElse(Vm.Params.defaultMemory),
          options.cpu.map(_.trim).filter(_.nonEmpty) match {
            case Some("*")   => Vm.Params.cpuCount.toString
            case Some(count) => count
            case None        => Vm.Params.defaultCpu
          },
          options.user.map(_.trim).filter(_.nonEmpty).getOrElse(Vm.Params.defaultUser),
          options.virtualization.getOrElse(true)
        )
    }
}
