package coursier.cli.publish.params

import java.nio.file.{Files, Paths}

import caseapp.Tag
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import coursier.cli.publish.conf.Conf
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
) {
  def withConf(conf: Conf): PublishParams = {

    var p = this

    for (o <- conf.organization.organization if p.metadata.organization.isEmpty)
      p = p.copy(
        metadata = p.metadata.copy(organization = Some(o))
      )

    // TODO Take conf.organization.url into account

    for (v <- conf.version if p.metadata.version.isEmpty)
      p = p.copy(
        metadata = p.metadata.copy(version = Some(v))
      )

    p
  }
}

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
    val batch = options.batch.getOrElse {
      coursier.TermDisplay.defaultFallbackMode
    }

    val res = (repositoryV, metadataV, singlePackageV, directoryV, checksumV, signatureV, verbosityV).mapN {
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

    // TODO Actually take conf file into account beforehand
    // So that e.g. its repository is taken into account and we do not default to sonatype here
    res.withEither { e =>
      for {
        p <- e
        // TODO Warn about ignored fields in conf file?
        confOpt <- options.conf match {
          case None =>
            val loadDefaultIfExists = !p.singlePackage.`package` &&
              p.directory.directories.isEmpty &&
              p.directory.sbtDirectories.forall(_ == Paths.get("."))
            if (loadDefaultIfExists) {
              val default = Paths.get("publish.json")
              val projectDefault = Paths.get("project/publish.json")
              if (Files.isRegularFile(default))
                Conf.load(default)
                  .left.map(NonEmptyList.of(_))
                  .right
                  .map(Some(_))
              else if (Files.isRegularFile(projectDefault))
                Conf.load(projectDefault)
                  .left.map(NonEmptyList.of(_))
                  .right
                  .map(Some(_))
              else
                Right(None)
            } else
              Right(None)
          case Some(c) =>
            val p = Paths.get(c)
            if (Files.exists(p)) {
              if (Files.isRegularFile(p))
                Conf.load(p)
                  .left.map(NonEmptyList.of(_))
                  .right
                  .map(Some(_))
              else
                Left(NonEmptyList.of(s"Conf file $c is not a file"))
            } else
              Left(NonEmptyList.of(s"Conf file $c not found"))
        }
      } yield confOpt.fold(p)(p.withConf)
    }
  }
}
