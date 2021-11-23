package coursier.cli.options

object OptionGroup {
  val help       = "Help"
  val verbosity  = "Verbosity"
  val setup      = "Setup"
  val bootstrap  = "Bootstrap"
  val install    = "Install"
  val uninstall  = "Uninstall"
  val update     = "Update"
  val native     = "Native launcher"
  val graalvm    = "Graalvm"
  val launch     = "Launch"
  val channel    = "App channel"
  val java       = "Java"
  val scripting  = "Scripting"
  val fetch      = "Fetch"
  val repository = "Repository"
  val dependency = "Dependency"
  val resolution = "Resolution"
  val cache      = "Cache"

  val order: Seq[String] = Seq(
    help,
    verbosity,
    setup,
    install,
    uninstall,
    update,
    bootstrap,
    native,
    graalvm,
    launch,
    channel,
    java,
    scripting,
    fetch,
    repository,
    dependency,
    resolution,
    cache
  )
}
