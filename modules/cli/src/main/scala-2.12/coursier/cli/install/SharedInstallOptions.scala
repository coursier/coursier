package coursier.cli.install

import caseapp.{ExtraName => Short, HelpMessage => Help, _}
import coursier.cli.options.CacheOptions

final case class SharedInstallOptions(

  @Recurse
    cacheOptions: CacheOptions = CacheOptions(),

  @Help("Quiet output")
  @Short("q")
    quiet: Int @@ Counter = Tag.of(0),

  @Help("Increase verbosity (specify several times to increase more)")
  @Short("v")
    verbose: Int @@ Counter = Tag.of(0),

  @Help("Force display of progress bars")
  @Short("P")
    progress: Boolean = false,

  @Short("f")
    forceUpdate: Boolean = false,

  graalvmHome: Option[String] = None,
  graalvmOption: List[String] = Nil,

  dir: Option[String] = None

)
