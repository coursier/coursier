package coursier.cli.options

object OptionGroup {
  val help       = "Help"
  val verbosity  = "Verbosity"
  val bootstrap  = "Bootstrap"
  val native     = "Native launcher"
  val graalvm    = "Graalvm"
  val launch     = "Launch"
  val channel    = "App channel"
  val fetch      = "Fetch"
  val repository = "Repository"
  val dependency = "Dependency"
  val resolution = "Resolution"
  val cache      = "Cache"

  val order: Seq[String] = Seq(
    help,
    verbosity,
    bootstrap,
    native,
    graalvm,
    launch,
    channel,
    fetch,
    repository,
    dependency,
    resolution,
    cache
  )
}
