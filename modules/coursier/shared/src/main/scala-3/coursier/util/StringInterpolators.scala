package coursier.util

import coursier.core._
import coursier.ivy.IvyRepository
import coursier.maven.MavenRepository
import coursier.parse.{DependencyParser, ModuleParser}

/** Scala 3 counterpart of the Scala 2 macro-based interpolators.
  *
  * The Scala 2 version validates the literal at compile-time; here we validate at runtime (the
  * inputs are literals in practice, so a malformed one still fails fast).
  */
object StringInterpolators {

  private def single(sc: StringContext): String =
    sc.parts match {
      case Seq(s) => s
      case _      => sys.error("Only a single String literal is allowed here")
    }

  private def parseModule(sc: StringContext): Module =
    ModuleParser.module(single(sc), scala.util.Properties.versionNumberString) match {
      case Left(e)    => sys.error(s"Error parsing module ${single(sc)}: $e")
      case Right(mod) => mod
    }

  extension (sc: StringContext) {

    def org(args: Any*): Organization =
      Organization(single(sc))

    def name(args: Any*): ModuleName =
      ModuleName(single(sc))

    def mod(args: Any*): Module =
      parseModule(sc)

    def excl(args: Any*): ModuleMatchers =
      ModuleMatchers(Set(ModuleMatcher(parseModule(sc))))

    def incl(args: Any*): ModuleMatchers =
      ModuleMatchers(Set.empty[ModuleMatcher], Set(ModuleMatcher(parseModule(sc))))

    def dep(args: Any*): Dependency =
      DependencyParser.dependency(
        single(sc),
        scala.util.Properties.versionNumberString,
        Configuration.empty
      ) match {
        case Left(e)    => sys.error(s"Error parsing dependency ${single(sc)}: $e")
        case Right(dep) => dep
      }

    def mvn(args: Any*): MavenRepository = {
      // validate the URI
      new java.net.URI(single(sc))
      MavenRepository.create(single(sc))
    }

    def ivy(args: Any*): IvyRepository =
      IvyRepository.parse(single(sc)) match {
        case Left(e)  => sys.error(s"Malformed Ivy repository '${single(sc)}': $e")
        case Right(r) => r
      }
  }
}
