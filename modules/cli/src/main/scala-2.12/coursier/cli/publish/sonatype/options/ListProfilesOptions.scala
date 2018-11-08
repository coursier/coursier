package coursier.cli.publish.sonatype.options

import caseapp.Recurse

final case class ListProfilesOptions(
  raw: Boolean = false,
  @Recurse
    sonatype: SonatypeOptions = SonatypeOptions()
)
