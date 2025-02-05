package coursier.core

import coursier.core.compatibility.encodeURIComponent
import coursier.util.{Artifact, EitherT, Monad}
import coursier.util.Monad.ops._
import coursier.version.{
  Latest => Latest0,
  Version => Version0,
  VersionConstraint => VersionConstraint0,
  VersionInterval => VersionInterval0
}
import dataclass.data

import scala.annotation.nowarn

trait Repository extends Serializable with ArtifactSource {

  def repr: String =
    toString

  def find0[F[_]](
    module: Module,
    version: Version0,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (ArtifactSource, Project)] =
    find(module, version.asString, fetch)(F): @nowarn

  @deprecated(
    "Use find0 instead - if overriding this method, override the other, and call it from here",
    "2.1.25"
  )
  def find[F[_]](
    module: Module,
    version: String,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (ArtifactSource, Project)]

  @deprecated("Unused by coursier", "2.1.25")
  def findMaybeInterval[F[_]](
    module: Module,
    version: VersionConstraint0,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (ArtifactSource, Project)] =
    version.preferred match {
      case Some(preferred) =>
        find0(module, preferred, fetch)
      case None =>
        versions(module, fetch).flatMap {
          case (versions0, versionsUrl) =>
            versions0.inInterval(version.interval) match {
              case None =>
                val reason = s"No version found for $version in $versionsUrl"
                EitherT[F, String, (ArtifactSource, Project)](F.point(Left(reason)))
              case Some(version0) =>
                find0(module, version0, fetch)
                  .map(t => t._1 -> t._2.withVersions(Some(versions0)))
            }
        }
    }

  @deprecated("Unused by coursier", "2.1.25")
  def findMaybeInterval[F[_]](
    module: Module,
    version: String,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (ArtifactSource, Project)] =
    findMaybeInterval(module, VersionConstraint0(version), fetch)(F)

  def findFromVersionConstraint[F[_]](
    module: Module,
    versionConstraint: VersionConstraint0,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (ArtifactSource, Project)] = {

    def checkVersion(version: Version0): F[Either[String, (ArtifactSource, Project)]] =
      find0(module, version, fetch)
        .run

    def fromInterval = {
      def checkVersions(versions: Iterator[Version0])
        : F[Either[String, (ArtifactSource, Project)]] =
        if (versions.hasNext)
          versions
            .foldLeft[F[Either[Seq[String], (ArtifactSource, Project)]]](F.point(Left(Nil))) {
              case (acc, version) =>
                acc.flatMap {
                  case Left(errors) =>
                    checkVersion(version)
                      .map(_.left.map(error => error +: errors))
                  case res @ Right(_) =>
                    F.point(res)
                }
            }
            .map(_.left.map(errors => errors.mkString(System.lineSeparator())))
        else
          F.point(Left(s"No version available in ${versionConstraint.interval.repr}"))

      versions(module, fetch).flatMap {
        case (versions, _) =>
          EitherT(checkVersions(versions.candidatesInInterval(versionConstraint.interval)))
      }
    }

    def fromLatest(latest: Latest0): EitherT[F, String, (ArtifactSource, Project)] =
      versions(module, fetch).flatMap {
        case (versions, _) =>
          val candidates = versions
            .latest(latest)
            .filter(versionConstraint.interval.contains)
          EitherT {
            candidates match {
              case None =>
                F.point(Left(
                  s"No version for ${latest.asString} available in ${versionConstraint.interval.repr}"
                ))
              case Some(candidate) =>
                checkVersion(candidate)
            }
          }
      }

    versionConstraint.latest match {
      case Some(latest) => fromLatest(latest)
      case None =>
        versionConstraint.preferred match {
          case Some(preferred) => EitherT(checkVersion(preferred))
          case None            => fromInterval
        }
    }
  }

  def completeOpt[F[_]: Monad](fetch: Repository.Fetch[F]): Option[Repository.Complete[F]] =
    None

  def versionsCheckHasModule: Boolean =
    false

  def versions[F[_]](
    module: Module,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Versions, String)] =
    versions(module, fetch, versionsCheckHasModule = false)

  def versions[F[_]](
    module: Module,
    fetch: Repository.Fetch[F],
    versionsCheckHasModule: Boolean
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Versions, String)] =
    if (versionsCheckHasModule)
      completeOpt(fetch) match {
        case None => fetchVersions(module, fetch)
        case Some(c) =>
          EitherT[F, String, Boolean](F.map(c.hasModule(module))(Right(_))).flatMap {
            case false =>
              EitherT(F.point[Either[String, (Versions, String)]](
                Left(s"${module.repr} not found on $repr")
              ))
            case true =>
              fetchVersions(module, fetch)
          }
      }
    else
      fetchVersions(module, fetch)

  protected def fetchVersions[F[_]](
    module: Module,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Versions, String)] =
    EitherT(F.point(Right((Versions.empty, ""))))
}

object Repository {

  type Fetch[F[_]] = Artifact => EitherT[F, String, String]

  implicit class ArtifactExtensions(val underlying: Artifact) extends AnyVal {
    def withDefaultChecksums: Artifact =
      underlying.withChecksumUrls(underlying.checksumUrls ++ Seq(
        "MD5"     -> (underlying.url + ".md5"),
        "SHA-1"   -> (underlying.url + ".sha1"),
        "SHA-256" -> (underlying.url + ".sha256")
      ))
    def withDefaultSignature: Artifact =
      underlying.withExtra(underlying.extra ++ Seq(
        "sig" ->
          Artifact(
            underlying.url + ".asc",
            Map.empty,
            Map.empty,
            changing = underlying.changing,
            optional = true,
            authentication = underlying.authentication
          )
            .withDefaultChecksums
      ))
  }

  trait Complete[F[_]] {
    def organization(prefix: String): F[Either[Throwable, Seq[String]]]
    def moduleName(organization: Organization, prefix: String): F[Either[Throwable, Seq[String]]]
    protected def moduleDirectory(module: Module): String
    def versions(
      module: Module,
      prefix: String
    ): F[Either[Throwable, Seq[Version0]]]

    private def org(
      orgInput: Complete.Input.Org
    )(implicit
      F: Monad[F]
    ): F[Either[Throwable, Complete.Result]] =
      F.map(organization(orgInput.input)) {
        case Left(e) =>
          Left(new Complete.CompletingOrgException(orgInput.input, e))
        case Right(l) =>
          Right(Complete.Result(orgInput, l))
      }

    private def name(
      nameInput: Complete.Input.Name
    )(implicit
      F: Monad[F]
    ): F[Either[Throwable, Complete.Result]] =
      F.map(moduleName(nameInput.organization, nameInput.input.drop(nameInput.from))) {
        case Left(e) =>
          Left(
            new Complete.CompletingNameException(
              nameInput.organization,
              nameInput.input,
              nameInput.from,
              e
            )
          )
        case Right(l) =>
          val l0 = l
            .filter(_.endsWith(nameInput.requiredSuffix))
            .map(_.stripSuffix(nameInput.requiredSuffix))
          Right(Complete.Result(nameInput, l0))
      }

    private def hasOrg(
      orgInput: Complete.Input.Org,
      partial: Boolean
    )(implicit
      F: Monad[F]
    ): F[Boolean] = {

      val check =
        F.map(org(orgInput)) { res =>
          res
            .toOption
            .exists { res =>
              res.completions.contains(orgInput.input) ||
              (partial && res.completions.exists(_.startsWith(orgInput.input + ".")))
            }
        }

      val idx = orgInput.input.lastIndexOf('.')
      if (idx > 0) {
        val truncatedInput = Complete.Input.Org(orgInput.input.take(idx))
        hasOrg(truncatedInput, partial = true).flatMap {
          case false =>
            F.point(false)
          case true =>
            check
        }
      }
      else // idx < 0 || idx == 0 (idx == 0 shouldn't happen often though)
        check
    }

    private def hasName(nameInput: Complete.Input.Name)(implicit F: Monad[F]): F[Boolean] =
      F.map(name(nameInput)) { res =>
        res
          .toOption
          .exists(_.completions.contains(nameInput.input.drop(nameInput.from)))
      }

    def hasModule(module: Module)(implicit F: Monad[F]): F[Boolean] =
      hasOrg(Complete.Input.Org(module.organization.value), partial = false).flatMap {
        case false => F.point(false)
        case true =>
          val prefix              = s"${module.organization.value}:"
          val moduleDirectoryName = moduleDirectory(module)
          hasName(Complete.Input.Name(
            module.organization,
            prefix + moduleDirectoryName,
            prefix.length,
            ""
          ))
      }

    final def complete(
      input: Complete.Input
    )(implicit
      F: Monad[F]
    ): F[Either[Throwable, Complete.Result]] = {

      // When completing names, we check if the org is there first.
      // When completing versions, we check if the org, then the name, are there first.
      // Goal is to never hit 404, that aren't cached.
      // So completing 'org.scala-lang:scala-library:' goes like:
      // - we complete first 'org', check that 'org' itself or 'org.' are in the results, stop if not
      // - we complete 'org.scala-lang', check that just 'org.scala-lang' is in the results
      // - we complete 'org.scala-lang:scala-library', check that 'org.scala-lang:scala-library' is in the results
      // - now that we know that 'org.scala-lang:scala-library' is a thing, we try to list its versions.
      // Each time we request something, we know that the parent ~element exists.

      def ver(versionInput: Complete.Input.Ver): F[Either[Throwable, Complete.Result]] =
        F.map(versions(versionInput.module, versionInput.input.drop(versionInput.from))) {
          case Left(e) =>
            Left(
              new Complete.CompletingVersionException(
                versionInput.module,
                versionInput.input,
                versionInput.from,
                e
              )
            )
          case Right(l) =>
            Right(Complete.Result(input, l.map(_.repr)))
        }

      def empty: F[Either[Throwable, Complete.Result]] = F.point(Right(Complete.Result(input, Nil)))

      input match {
        case orgInput: Complete.Input.Org =>
          val idx = orgInput.input.lastIndexOf('.')
          if (idx < 0)
            org(orgInput)
          else
            hasOrg(Complete.Input.Org(orgInput.input.take(idx)), partial = true).flatMap {
              case false => empty
              case true  => org(orgInput)
            }
        case nameInput: Complete.Input.Name =>
          hasOrg(nameInput.orgInput, partial = false).flatMap {
            case false => empty
            case true  => name(nameInput)
          }
        case verInput: Complete.Input.Ver =>
          hasOrg(verInput.orgInput, partial = false).flatMap {
            case false => empty
            case true =>
              hasName(verInput.nameInput).flatMap {
                case false => empty
                case true  => ver(verInput)
              }
          }
      }
    }

    final def complete(
      input: String,
      scalaVersion: String,
      scalaBinaryVersion: String
    )(implicit
      F: Monad[F]
    ): F[Either[Throwable, Complete.Result]] =
      Complete.parse(input, scalaVersion, scalaBinaryVersion) match {
        case Left(e) =>
          F.point(Left(e))
        case Right(input0) =>
          complete(input0)
      }
  }

  object Complete {
    sealed abstract class Input extends Product with Serializable {
      def input: String
      def from: Int
    }
    object Input {
      @data class Org(input: String) extends Input {
        def from: Int = 0
      }
      @data class Name(organization: Organization, input: String, from: Int, requiredSuffix: String)
          extends Input {
        def orgInput: Org =
          Org(organization.value)
      }
      @data class Ver(module: Module, input: String, from: Int) extends Input {
        def orgInput: Org =
          nameInput.orgInput
        def nameInput: Name = {
          val truncatedInput = module.repr
          Name(module.organization, truncatedInput, module.organization.value.length + 1, "")
        }
      }
    }

    def parse(
      input: String,
      scalaVersion: String,
      scalaBinaryVersion: String
    ): Either[Throwable, Input] = {

      val idx = input.lastIndexOf(':')
      if (idx < 0)
        Right(Input.Org(input))
      else
        input.take(idx).split("\\:", -1) match {
          case Array(org) =>
            val org0 = Organization(org)
            Right(Input.Name(org0, input, idx + 1, ""))
          case Array(org, "") =>
            val org0 = Organization(org)
            Right(Input.Name(org0, input, idx + 1, "_" + scalaBinaryVersion))
          case Array(org, "", "") =>
            val org0 = Organization(org)
            Right(Input.Name(org0, input, idx + 1, "_" + scalaVersion))
          case Array(org, name) =>
            val org0  = Organization(org)
            val name0 = ModuleName(name)
            val mod   = Module(org0, name0, Map())
            Right(Input.Ver(mod, input, idx + 1))
          case Array(org, "", name) if scalaBinaryVersion.nonEmpty =>
            val org0  = Organization(org)
            val name0 = ModuleName(name + "_" + scalaBinaryVersion)
            val mod   = Module(org0, name0, Map())
            Right(Input.Ver(mod, input, idx + 1))
          case Array(org, "", "", name) if scalaBinaryVersion.nonEmpty =>
            val org0  = Organization(org)
            val name0 = ModuleName(name + "_" + scalaVersion)
            val mod   = Module(org0, name0, Map())
            Right(Input.Ver(mod, input, idx + 1))
          case _ =>
            Left(new Complete.MalformedInput(input))
        }
    }

    @data class Result(input: Input, completions: Seq[String])

    final class CompletingOrgException(input: String, cause: Throwable = null)
        extends Exception(s"Completing organization '$input'", cause)
    final class CompletingNameException(
      organization: Organization,
      input: String,
      from: Int,
      cause: Throwable = null
      // format: off
    ) extends Exception(
      s"Completing module name '${input.drop(from)}' for organization ${organization.value}",
      cause
    )
    // format: on
    final class CompletingVersionException(
      module: Module,
      input: String,
      from: Int,
      cause: Throwable = null
    ) extends Exception(s"Completing version '${input.drop(from)}' for module $module", cause)
    final class MalformedInput(input: String)
        extends Exception(s"Malformed input '$input'")
  }

  trait VersionApi extends Repository {

    @deprecated(
      "Use find0 instead - if overriding this method, override the other, and call it from here",
      "2.1.25"
    )
    def find[F[_]](
      module: Module,
      version: String,
      fetch: Repository.Fetch[F]
    )(implicit
      F: Monad[F]
    ): EitherT[F, String, (ArtifactSource, Project)] =
      find0(module, Version0(version), fetch)(F)

  }
}
