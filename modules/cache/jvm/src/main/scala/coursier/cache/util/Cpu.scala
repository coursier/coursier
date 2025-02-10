package coursier.cache.util

import java.util.Locale

sealed abstract class Cpu extends Product with Serializable

object Cpu {
  case object X86_64 extends Cpu
  case object Arm64  extends Cpu

  def apply(arch: String): Option[Cpu] =
    arch.toLowerCase(Locale.ROOT) match {
      case "amd64" | "x86_64"  => Some(X86_64)
      case "arm64" | "aarch64" => Some(Arm64)
      case _                   => None
    }

  private lazy val currentArch =
    sys.props.get("os.arch").flatMap(apply(_))

  def apply(): Option[Cpu] =
    currentArch
  def get(): Cpu =
    currentArch.getOrElse {
      sys.error(s"Unrecognized CPU type (${sys.props.getOrElse("os.arch", "")})")
    }
}
