package coursier.cli.publish.sonatype

import caseapp._

final case class SonatypeOptions(
  raw: Boolean = false,
  profile: Option[String] = None,
  profileId: Option[String] = None,
  @Name("repo")
    repository: Option[String] = None,
  description: String = "",
  listProfiles: Boolean = false,
  list: Option[Boolean] = None,
  cleanList: Option[Boolean] = None,
  create: Boolean = false,
  close: Boolean = false,
  promote: Boolean = false,
  drop: Boolean = false,
  base: Option[String] = None,
  user: Option[String] = None,
  password: Option[String] = None,
  @Name("v")
    verbose: Int @@ Counter = Tag.of(0)
)
