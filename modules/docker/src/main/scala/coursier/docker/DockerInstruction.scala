package coursier.docker

sealed abstract class DockerInstruction extends Product with Serializable {
  def asNonHead: Option[DockerInstruction.NonHead]
}

object DockerInstruction {
  case class From(imageName: String) extends DockerInstruction {
    def asNonHead: Option[DockerInstruction.NonHead] = None
  }

  sealed abstract class NonHead extends DockerInstruction {
    def asNonHead: Option[DockerInstruction.NonHead] =
      Some(this)
  }

  sealed abstract class Metadata       extends NonHead
  case class WorkDir(path: String)     extends Metadata
  case class Cmd(command: Seq[String]) extends Metadata
  case class Expose(port: Int)         extends Metadata

  sealed abstract class MakeLayer           extends NonHead
  case class Copy(from: String, to: String) extends MakeLayer
  case class Run(command: Seq[String])      extends MakeLayer
}
