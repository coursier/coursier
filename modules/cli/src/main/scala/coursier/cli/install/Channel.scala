package coursier.cli.install

import coursier.core.Module

abstract class Channel extends Product with Serializable {
  def repr: String
}

object Channel {

  final case class FromModule(module: Module) extends Channel {
    def repr: String =
      module.repr
  }

  final case class FromUrl(url: String) extends Channel {
    def repr: String =
      url
  }

  def module(module: Module): FromModule =
    FromModule(module)

  def url(url: String): FromUrl =
    FromUrl(url)

}
