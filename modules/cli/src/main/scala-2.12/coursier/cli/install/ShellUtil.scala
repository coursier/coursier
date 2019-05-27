package coursier.cli.install

object ShellUtil {

  def rcFileOpt: Option[String] =
    sys.env.get("SHELL").map(_.split('/').last).flatMap {
      case "zsh" => Some("~/.zshrc")
      case "bash" => Some("~/.bashrc")
      case _ => None
    }

}
