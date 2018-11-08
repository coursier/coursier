package coursier.cli

import caseapp.CommandParser
import caseapp.core.help.CommandsHelp
import coursier.cli.publish.Publish
import coursier.cli.publish.sonatype.{CreateStagingRepository, ListProfiles}
import coursier.cli.resolve.Resolve

object CoursierCommand {

  val parser =
    CommandParser.nil
      .add(Bootstrap)
      .add(Fetch)
      .add(Launch)
      .add(Publish)
      .add(Resolve)
      .add(ListProfiles, "sonatype-list-profiles")
      .add(CreateStagingRepository, "sonatype-create-staging-repository")
      .add(SparkSubmit)
      .reverse

  val help =
    CommandsHelp.nil
      .add(Bootstrap)
      .add(Fetch)
      .add(Launch)
      .add(Publish)
      .add(Resolve)
      .add(ListProfiles, "sonatype-list-profiles")
      .add(CreateStagingRepository, "sonatype-create-staging-repository")
      .add(SparkSubmit)
      .reverse

}
