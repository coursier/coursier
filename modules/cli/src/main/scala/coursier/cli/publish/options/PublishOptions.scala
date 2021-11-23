package coursier.cli.publish.options

import caseapp._
import coursier.cli.options.CacheOptions

// format: off
@HelpMessage("[Experimental] Publish an artifact to a maven repository.")
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

  @Recurse
    cacheOptions: CacheOptions = CacheOptions(),

  @Name("q")
    quiet: Option[Boolean] = None,

  @Name("v")
    verbose: Int @@ Counter = Tag.of(0),

  dummy: Boolean = false,

  @HelpMessage("Disable interactive output")
    batch: Option[Boolean] = None,

  conf: Option[String] = None,

  sbtOutputFrame: Int = 10,

  parallelUpload: Option[Boolean] = None,

  urlSuffix: Option[String] = None

)
// format: on

object PublishOptions {
  implicit val parser = Parser[PublishOptions]
  implicit val help   = caseapp.core.help.Help[PublishOptions]
}
