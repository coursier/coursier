package coursier.cli.publish.options

import caseapp._

final case class PublishOptions(

  @Recurse
    repositoryOptions: RepositoryOptions = RepositoryOptions(),

  @Recurse
    metadataOptions: MetadataOptions = MetadataOptions(),

  @Recurse
    singlePackageOptions: SinglePackageOptions = SinglePackageOptions(),

  @Recurse
    directoryOptions: DirectoryOptions = DirectoryOptions(),

  @Recurse
    checksumOptions: ChecksumOptions = ChecksumOptions(),

  @Recurse
    signatureOptions: SignatureOptions = SignatureOptions(),

  @Name("q")
    quiet: Option[Boolean] = None,

  @Name("v")
    verbose: Int @@ Counter = Tag.of(0),

  @Name("n")
    dummy: Boolean = false,

  @HelpMessage("Disable interactive output")
    batch: Boolean = false,

  sbtOutputFrame: Int = 10

)

object PublishOptions {
  implicit val parser = Parser[PublishOptions]
  implicit val help = caseapp.core.help.Help[PublishOptions]
}
