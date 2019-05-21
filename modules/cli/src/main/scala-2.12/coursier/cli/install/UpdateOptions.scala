package coursier.cli.install

import caseapp.Recurse

final case class UpdateOptions(

  @Recurse
    sharedInstallOptions: SharedInstallOptions = SharedInstallOptions(),

  dir: Option[String]
)
