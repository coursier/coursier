package coursier.cli.publish.params

import caseapp.Tag
import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.publish.options.PublishOptions

final case class PublishParams(
  repository: RepositoryParams,
  metadata: MetadataParams,
  singlePackage: SinglePackageParams,
  directory: DirectoryParams,
  checksum: ChecksumParams,
  signature: SignatureParams,
  verbosity: Int,
  dummy: Boolean,
  batch: Boolean,
  sbtOutputFrame: Option[Int]
)

object PublishParams {
  def apply(options: PublishOptions, args: Seq[String]): ValidatedNel[String, PublishParams] = {

    // FIXME Get from options
    val defaultScalaVersion = scala.util.Properties.versionNumberString

    val repositoryV = RepositoryParams(options.repositoryOptions)
    val metadataV = MetadataParams(options.metadataOptions, defaultScalaVersion)
    val singlePackageV = SinglePackageParams(options.singlePackageOptions)
    val directoryV = DirectoryParams(options.directoryOptions, args)
    val checksumV = ChecksumParams(options.checksumOptions)
    val signatureV = SignatureParams(options.signatureOptions)

    val verbosityV =
      (options.quiet, Tag.unwrap(options.verbose)) match {
        case (Some(true), 0) =>
          Validated.validNel(-1)
        case (Some(true), n) =>
          assert(n > 0)
          Validated.invalidNel("Cannot specify both --quiet and --verbose")
        case (_, n) =>
          Validated.validNel(n)
      }

    val sbtOutputFrame =
      Some(options.sbtOutputFrame).filter(_ > 0)

    val dummy = options.dummy
    val batch = options.batch

    (repositoryV, metadataV, singlePackageV, directoryV, checksumV, signatureV, verbosityV).mapN {
      (repository, metadata, singlePackage, directory, checksum, signature, verbosity) =>
        PublishParams(
          repository,
          metadata,
          singlePackage,
          directory,
          checksum,
          signature,
          verbosity,
          dummy,
          batch,
          sbtOutputFrame
        )
    }
  }
}
