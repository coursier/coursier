package coursier.cli.install

import coursier.core.Module

abstract class Channel extends Product with Serializable

object Channel {

  final case class FromModule(module: Module) extends Channel

  def module(module: Module): FromModule =
    FromModule(module)

}
