package coursier.cli.jvm

import caseapp._
import coursier.cli.options.RepositoryOptions

// format: off
final case class SharedJavaOptions(

  @Group("Java")
    jvm: Option[String] = None,

  @Group("Java")
  @Hidden
    systemJvm: Option[Boolean] = None,

  @Group("Java")
  @Hidden
    update: Boolean = false,

  @Group("Java")
    jvmIndex: Option[String] = None,

  @Recurse
    repositoryOptions: RepositoryOptions = RepositoryOptions()
)
// format: on
