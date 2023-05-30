package coursier.env

object ShellUtil {

  def rcFileOpt(
    getEnv: Option[String => Option[String]] = Some(k => Option(System.getenv(k)))
  ): Option[String] =
    shell(getEnv) match {
      case Some(Shell.Zsh)  => Some("~/.zshrc")
      case Some(Shell.Bash) => Some("~/.bashrc")
      // TODO in the future we might want to support `.config/fish/config.fish` here
      case _ => None
    }

  def shell(
    getEnv: Option[String => Option[String]] = Some(k => Option(System.getenv(k)))
  ): Option[Shell] =
    getEnv
      .flatMap(_("SHELL"))
      .map(_.split('/').last)
      .flatMap {
        case "zsh"  => Some(Shell.Zsh)
        case "bash" => Some(Shell.Bash)
        case "fish" => Some(Shell.Fish)
        case _      => None
      }

}

sealed trait Shell
object Shell {
  case object Bash extends Shell
  case object Zsh  extends Shell
  case object Fish extends Shell
}
