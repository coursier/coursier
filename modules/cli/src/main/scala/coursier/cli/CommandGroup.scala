package coursier.cli

object CommandGroup {
  val install: String  = "Install application"
  val channel: String  = "Application channel"
  val java: String     = "Java"
  val launcher: String = "Launcher"
  val resolve: String  = "Resolution"

  val order: Seq[String] = Seq(install, channel, java, launcher, resolve)
}
