package coursier.tests

import coursier.Repositories
import coursier.core.{Dependency, Info, Overrides, Project, Resolution}
import coursier.params.rule.SameVersion
import coursier.util.StringInterpolators._
import coursier.version.{Version, VersionConstraint}
import utest._

import coursier.tests.AssertCompat.assert

object SameVersionTests extends TestSuite {

  private val library      = dep"org.scala-lang:scala-library:2.11.12"
  private val actors       = dep"org.scala-lang:scala-actors:2.11.0"
  private val dependencies = Seq(library, actors)
  private val rule         = SameVersion(library.module, actors.module)

  private def resolution(forced: Dependency): Resolution =
    Resolution().copy(
      rootDependencies = dependencies,
      forceVersions0 = Map(forced.moduleVersionConstraint),
      projectCache0 = (dependencies :+ dep"org.scala-lang:scala-actors:2.11.12").map { dep =>
        dep.moduleVersionConstraint -> (
          Repositories.central,
          Project(
            module = dep.module,
            version0 = Version(dep.versionConstraint.asString),
            dependencies0 = Nil,
            configurations = Map.empty,
            parent0 = None,
            dependencyManagement0 = Nil,
            properties = Nil,
            profiles = Nil,
            versions = None,
            snapshotVersioning = None,
            packagingOpt = None,
            relocated = false,
            actualVersionOpt0 = None,
            publications0 = Nil,
            info = Info.empty,
            overrides = Overrides.empty,
            variants = Map.empty,
            variantPublications = Map.empty
          )
        )
      }.toMap
    ).withDependencies(dependencies.toSet)

  val tests = Tests {
    test("matchingForcedVersion") {
      val res    = resolution(library)
      val result = rule.tryResolve(res, rule.check(res).get)
      assert(result.isRight)
      val updated = result.toOption.get
      assert(rule.check(updated).isEmpty)
      assert(updated.forceVersions0(library.module) == library.versionConstraint)
      assert(updated.forceVersions0(actors.module) == VersionConstraint("2.11.12"))
    }

    test("conflictingForcedVersion") {
      val res    = resolution(actors)
      val result = rule.tryResolve(res, rule.check(res).get)
      assert(result match {
        case Left(_: SameVersion.CantForceSameVersion) => true
        case _                                         => false
      })
    }
  }
}
