package coursier.core

import coursier.core.compatibility.encodeURIComponent
import coursier.util.{EitherT, Monad}

trait Repository extends Product with Serializable with Artifact.Source {
  def find[F[_]](
    module: Module,
    version: String,
    fetch: Repository.Fetch[F]
  )(implicit
    F: Monad[F]
  ): EitherT[F, String, (Artifact.Source, Project)]

  def completeOpt[F[_]: Monad](fetch: Repository.Fetch[F]): Option[Repository.Complete[F]] =
    None
}

object Repository {

  type Fetch[F[_]] = Artifact => EitherT[F, String, String]


  implicit class ArtifactExtensions(val underlying: Artifact) extends AnyVal {
    def withDefaultChecksums: Artifact =
      underlying.copy(checksumUrls = underlying.checksumUrls ++ Seq(
        "MD5" -> (underlying.url + ".md5"),
        "SHA-1" -> (underlying.url + ".sha1"),
        "SHA-256" -> (underlying.url + ".sha256")
      ))
    def withDefaultSignature: Artifact =
      underlying.copy(extra = underlying.extra ++ Seq(
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
    def versions(module: Module, prefix: String): F[Either[Throwable, Seq[String]]]

    final def complete(input: Complete.Input)(implicit F: Monad[F]): F[Either[Throwable, Complete.Result]] =
      input match {
        case Complete.Input.Org(prefix) =>
          F.map(organization(prefix)) {
            case Left(e) =>
              Left(new Complete.CompletingOrgException(prefix, e))
            case Right(l) =>
              Right(Complete.Result(input, l))
          }
        case Complete.Input.Name(org, input1, from, requiredSuffix) =>
          F.map(moduleName(org, input1.drop(from))) {
            case Left(e) =>
              Left(new Complete.CompletingNameException(org, input1, from, e))
            case Right(l) =>
              val l0 = l.filter(_.endsWith(requiredSuffix)).map(_.stripSuffix(requiredSuffix))
              Right(Complete.Result(input, l0))
          }
        case Complete.Input.Ver(mod, input1, from) =>
          F.map(versions(mod, input1.drop(from))) {
            case Left(e) =>
              Left(new Complete.CompletingVersionException(mod, input1, from, e))
            case Right(l) =>
              Right(Complete.Result(input, l))
          }
      }

    final def complete(input: String, scalaVersion: String, scalaBinaryVersion: String)(implicit F: Monad[F]): F[Either[Throwable, Complete.Result]] =
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
      final case class Org(input: String) extends Input {
        def from: Int = 0
      }
      final case class Name(organization: Organization, input: String, from: Int, requiredSuffix: String) extends Input
      final case class Ver(module: Module, input: String, from: Int) extends Input
    }

    def parse(input: String, scalaVersion: String, scalaBinaryVersion: String): Either[Throwable, Input] = {

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
            val org0 = Organization(org)
            val name0 = ModuleName(name)
            val mod = Module(org0, name0, Map())
            Right(Input.Ver(mod, input, idx + 1))
          case Array(org, "", name) if scalaBinaryVersion.nonEmpty =>
            val org0 = Organization(org)
            val name0 = ModuleName(name + "_" + scalaBinaryVersion)
            val mod = Module(org0, name0, Map())
            Right(Input.Ver(mod, input, idx + 1))
          case Array(org, "", "", name) if scalaBinaryVersion.nonEmpty =>
            val org0 = Organization(org)
            val name0 = ModuleName(name + "_" + scalaVersion)
            val mod = Module(org0, name0, Map())
            Right(Input.Ver(mod, input, idx + 1))
          case _ =>
            Left(new Complete.MalformedInput(input))
        }
    }

    final case class Result(input: Input, completions: Seq[String])

    final class CompletingOrgException(input: String, cause: Throwable = null)
      extends Exception(s"Completing organization '$input'", cause)
    final class CompletingNameException(organization: Organization, input: String, from: Int, cause: Throwable = null)
      extends Exception(s"Completing module name '${input.drop(from)}' for organization ${organization.value}", cause)
    final class CompletingVersionException(module: Module, input: String, from: Int, cause: Throwable = null)
      extends Exception(s"Completing version '${input.drop(from)}' for module $module", cause)
    final class MalformedInput(input: String)
      extends Exception(s"Malformed input '$input'")
  }
}

