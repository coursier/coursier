package coursier.tests

import coursier.{Repositories, Resolve}
import coursier.error.conflict.{StrictRule, UnsatisfiedRule}
import coursier.graph.Conflict
import coursier.params.ResolutionParams
import coursier.params.rule.{
  AlwaysFail,
  DontBumpRootDependencies,
  RuleResolution,
  SameVersion,
  Strict
}
import coursier.util.{ModuleMatcher, ModuleMatchers}
import coursier.util.StringInterpolators._
import coursier.version.{ConstraintReconciliation, Version, VersionConstraint}
import utest._

import scala.async.Async.{async, await}

object ResolveRulesTests extends TestSuite {

  import TestHelpers.{ec, cache, validateDependencies}

  val tests = Tests {

    test("alwaysFail") {
      test("wrongRuleTryResolve") {
        async {

          val rule = AlwaysFail(doTryResolve = true)
          // should fail anyway (tryResolve of AlwaysFail does nothing)
          val ruleRes = RuleResolution.TryResolve

          val params = ResolutionParams()
            .addRule(rule, ruleRes)

          val ex = await {
            Resolve()
              .noMirrors
              .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
              .withResolutionParams(params)
              .withCache(cache)
              .future()
              .failed
          }

          ex match {
            case f: UnsatisfiedRule =>
              assert(f.rule == rule)
              assert(f.isInstanceOf[AlwaysFail.Nope])
            case _ =>
              throw new Exception("Unexpected exception type", ex)
          }
        }
      }

      test("failRuleTryResolve") {
        async {

          val rule = AlwaysFail(doTryResolve = false)
          // should fail anyway (tryResolve of AlwaysFail fails anyway)
          val ruleRes = RuleResolution.TryResolve

          val params = ResolutionParams()
            .addRule(rule, ruleRes)

          val ex = await {
            Resolve()
              .noMirrors
              .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
              .withResolutionParams(params)
              .withCache(cache)
              .future()
              .failed
          }

          ex match {
            case f: AlwaysFail.NopityNope =>
              assert(f.rule == rule)
              assert(f.conflict.isInstanceOf[AlwaysFail.Nope])
            case _ =>
              throw new Exception("Unexpected exception type", ex)
          }
        }
      }

      test("failRuleResolution") {
        async {

          val rule    = AlwaysFail()
          val ruleRes = RuleResolution.Fail

          val params = ResolutionParams()
            .addRule(rule, ruleRes)

          val ex = await {
            Resolve()
              .noMirrors
              .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
              .withResolutionParams(params)
              .withCache(cache)
              .future()
              .failed
          }

          ex match {
            case f: StrictRule =>
              assert(f.rule == rule)
              assert(f.conflict.isInstanceOf[AlwaysFail.Nope])
            case _ =>
              throw new Exception("Unexpected exception type", ex)
          }
        }
      }
    }

    test("sameVersionRule") {
      test {
        async {

          val params = ResolutionParams()
            .withScalaVersion("2.12.7")
            .addRule(
              SameVersion(
                mod"com.fasterxml.jackson.core:jackson-annotations",
                mod"com.fasterxml.jackson.core:jackson-core",
                mod"com.fasterxml.jackson.core:jackson-databind"
              ),
              RuleResolution.TryResolve
            )

          val res = await {
            Resolve()
              .noMirrors
              .addDependencies(dep"sh.almond:scala-kernel_2.12.7:0.2.2")
              .addRepositories(Repositories.jitpack)
              .withResolutionParams(params)
              .withCache(cache)
              .future()
          }

          await(validateDependencies(res, params))
        }
      }

      test {
        async {

          val params = ResolutionParams()
            .withScalaVersion("2.12.7")
            .addRule(
              SameVersion(mod"com.fasterxml.jackson.core:jackson-*"),
              RuleResolution.TryResolve
            )

          val res = await {
            Resolve()
              .noMirrors
              .addDependencies(dep"sh.almond:scala-kernel_2.12.7:0.2.2")
              .addRepositories(Repositories.jitpack)
              .withResolutionParams(params)
              .withCache(cache)
              .future()
          }

          await(validateDependencies(res, params))
        }
      }
    }

    test("strict") {
      test("fail") {
        async {

          val rule    = Strict()
          val ruleRes = RuleResolution.Fail

          val params = ResolutionParams()
            .addRule(rule, ruleRes)

          val ex = await {
            Resolve()
              .noMirrors
              .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
              .withResolutionParams(params)
              .withCache(cache)
              .future()
              .failed
          }

          ex match {
            case f: StrictRule =>
              assert(f.rule == rule)
              assert(f.conflict.isInstanceOf[Strict.EvictedDependencies])
            case _ =>
              throw new Exception("Unexpected exception type", ex)
          }
        }
      }

      test("for roots") {
        async {

          val rule    = Strict()
          val ruleRes = RuleResolution.Fail

          val params = ResolutionParams()
            .addRule(rule, ruleRes)

          val ex = await {
            Resolve()
              .noMirrors
              .addDependencies(
                dep"org.typelevel:cats-effect_2.11:1.3.1",
                dep"org.typelevel:cats-core_2.11:1.5.0"
              )
              .withResolutionParams(params)
              .withCache(cache)
              .future()
              .failed
          }

          val expectedEvicted = Seq(
            Conflict(
              mod"org.typelevel:cats-core_2.11",
              Version("1.6.0"),
              VersionConstraint("1.5.0"),
              wasExcluded = false,
              mod"org.typelevel:cats-core_2.11",
              VersionConstraint("1.5.0")
            )
          )
          val evicted = ex match {
            case f: StrictRule =>
              assert(f.rule == rule)
              assert(f.conflict.isInstanceOf[Strict.EvictedDependencies])
              f.conflict match {
                case e: Strict.EvictedDependencies => e.evicted.map(_.conflict)
                case _                             => ???
              }
            case _ =>
              throw new Exception("Unexpected exception type", ex)
          }

          if (evicted != expectedEvicted) {
            pprint.err.log(expectedEvicted)
            pprint.err.log(evicted)
          }
          assert(expectedEvicted == evicted)
        }
      }

      test("with intervals") {
        async {

          val rule = Strict()
            .withExclude(Set(
              ModuleMatcher(mod"org.scala-lang:*")
            ))
          val ruleRes = RuleResolution.Fail

          val params = ResolutionParams()
            .addRule(rule, ruleRes)

          val ex = await {
            Resolve()
              .noMirrors
              .addDependencies(
                dep"com.github.alexarchambault:argonaut-shapeless_6.2_2.12:1.2.0-M4",
                dep"com.chuusai:shapeless_2.12:[2.3.3,2.3.4)"
              )
              .withResolutionParams(params)
              .withCache(cache)
              .future()
              .failed
          }

          val expectedEvicted = Seq(
            Conflict(
              mod"com.chuusai:shapeless_2.12",
              Version("2.3.4-M1"),
              VersionConstraint("2.3.2"),
              wasExcluded = false,
              mod"com.github.alexarchambault:argonaut-shapeless_6.2_2.12",
              VersionConstraint("1.2.0-M4")
            )
          )
          val evicted = ex match {
            case f: StrictRule =>
              assert(f.rule == rule)
              assert(f.conflict.isInstanceOf[Strict.EvictedDependencies])
              f.conflict match {
                case e: Strict.EvictedDependencies => e.evicted.map(_.conflict)
                case _                             => ???
              }
            case _ =>
              throw new Exception("Unexpected exception type", ex)
          }

          if (evicted != expectedEvicted) {
            pprint.err.log(expectedEvicted)
            pprint.err.log(evicted)
          }
          assert(expectedEvicted == evicted)
        }
      }

      test("ignore if forced version") {
        test("do ignore") {
          async {

            val rule = Strict()
              .withExclude(Set(
                ModuleMatcher(mod"org.scala-lang:*")
              ))
            val ruleRes = RuleResolution.Fail

            val params = ResolutionParams()
              .addRule(rule, ruleRes)
              .addForceVersion0(mod"com.chuusai:shapeless_2.12" -> VersionConstraint("2.3.3"))

            val res = await {
              Resolve()
                .noMirrors
                .addDependencies(
                  dep"com.github.alexarchambault:argonaut-shapeless_6.2_2.12:1.2.0-M4",
                  dep"com.chuusai:shapeless_2.12:[2.3.3,2.3.4)"
                )
                .withResolutionParams(params)
                .withCache(cache)
                .future()
            }

            await(validateDependencies(res, params))
          }
        }

        test("do not ignore") {
          async {

            val rule = Strict()
              .withExclude(Set(
                ModuleMatcher(mod"org.scala-lang:*")
              ))
              .withIgnoreIfForcedVersion(false)
            val ruleRes = RuleResolution.Fail

            val params = ResolutionParams()
              .addRule(rule, ruleRes)
              .addForceVersion0(mod"com.chuusai:shapeless_2.12" -> VersionConstraint("2.3.3"))

            val ex = await {
              Resolve()
                .noMirrors
                .addDependencies(
                  dep"com.github.alexarchambault:argonaut-shapeless_6.2_2.12:1.2.0-M4",
                  dep"com.chuusai:shapeless_2.12:[2.3.3,2.3.4)"
                )
                .withResolutionParams(params)
                .withCache(cache)
                .future()
                .failed
            }

            val expectedEvicted = Seq(
              Conflict(
                mod"com.chuusai:shapeless_2.12",
                Version("2.3.3"),
                VersionConstraint("2.3.2"),
                wasExcluded = false,
                mod"com.github.alexarchambault:argonaut-shapeless_6.2_2.12",
                VersionConstraint("1.2.0-M4")
              )
            )
            val evicted = ex match {
              case f: StrictRule =>
                assert(f.rule == rule)
                assert(f.conflict.isInstanceOf[Strict.EvictedDependencies])
                f.conflict match {
                  case e: Strict.EvictedDependencies => e.evicted.map(_.conflict)
                  case _                             => ???
                }
              case _ =>
                throw new Exception("Unexpected exception type", ex)
            }

            assert(evicted == expectedEvicted)
          }
        }
      }

      test("viaReconciliation") {
        async {

          val params = ResolutionParams()
            .addReconciliation(ModuleMatchers.all -> ConstraintReconciliation.Strict)

          val ex = await {
            Resolve()
              .noMirrors
              .addDependencies(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8")
              .withResolutionParams(params)
              .withCache(cache)
              .future()
              .failed
          }

          ex match {
            case f: StrictRule =>
              assert(f.conflict.isInstanceOf[Strict.EvictedDependencies])
            case _ =>
              throw new Exception("Unexpected exception type", ex)
          }
        }
      }
    }

    test("semVer reconciliation") {
      test("strict check") {
        async {

          val params = ResolutionParams()
            .addReconciliation(ModuleMatchers.all -> ConstraintReconciliation.Strict)

          val ex = await {
            Resolve()
              .noMirrors
              .addBoms0(dep"org.apache.logging.log4j:log4j-bom:2.23.1".moduleVersionConstraint)
              .addDependencies(
                dep"com.github.alexarchambault:argonaut-shapeless_6.2_2.11:1.2.0-M11",
                dep"io.argonaut:argonaut_2.11:6.1",
                dep"org.apache.logging.log4j:log4j-api" // "any" version must not be reconciled
              )
              .withResolutionParams(params)
              .withCache(cache)
              .future()
              .failed
          }

          ex match {
            case f: StrictRule =>
              assert(f.conflict.isInstanceOf[Strict.EvictedDependencies])
              val evicted = f.conflict.asInstanceOf[Strict.EvictedDependencies]
              assert(evicted.evicted.length == 2)
              val conflictedModules = evicted.evicted.map(_.conflict.module).toSet
              val expectedConflictedModules = Set(
                mod"io.argonaut:argonaut_2.11",
                mod"org.scala-lang:scala-library"
              )
              assert(conflictedModules == expectedConflictedModules)
            case _ =>
              throw new Exception("Unexpected exception type", ex)
          }
        }
      }

      test("conflict") {
        async {

          val params = ResolutionParams()
            .addReconciliation(ModuleMatchers.all -> ConstraintReconciliation.SemVer)

          val ex = await {
            Resolve()
              .noMirrors
              .addBoms0(dep"org.apache.logging.log4j:log4j-bom:2.23.1".moduleVersionConstraint)
              .addDependencies(
                dep"com.github.alexarchambault:argonaut-shapeless_6.2_2.11:1.2.0-M11",
                dep"io.argonaut:argonaut_2.11:6.1",
                dep"org.apache.logging.log4j:log4j-api" // "any" version must not introduce conficts
              )
              .withResolutionParams(params)
              .withCache(cache)
              .future()
              .failed
          }

          ex match {
            case f: StrictRule =>
              assert(f.conflict.isInstanceOf[Strict.EvictedDependencies])
              val evicted = f.conflict.asInstanceOf[Strict.EvictedDependencies]
              assert(evicted.evicted.length == 1)
              val conflict = evicted.evicted.head.conflict
              val expectedConflict = Conflict(
                mod"io.argonaut:argonaut_2.11",
                Version("6.2.3"),
                VersionConstraint("6.1"),
                wasExcluded = false,
                mod"io.argonaut:argonaut_2.11",
                VersionConstraint("6.1")
              )
              assert(conflict == expectedConflict)
            case _ =>
              throw new Exception("Unexpected exception type", ex)
          }
        }
      }

      test("no conflict") {
        async {

          val params = ResolutionParams()
            .addReconciliation(ModuleMatchers.all -> ConstraintReconciliation.SemVer)

          val res = await {
            Resolve()
              .noMirrors
              .addBoms0(dep"org.apache.logging.log4j:log4j-bom:2.23.1".moduleVersionConstraint)
              .addDependencies(
                dep"com.github.alexarchambault:argonaut-shapeless_6.2_2.11:1.2.0-M11",
                dep"io.argonaut:argonaut_2.11:6.2",
                dep"org.apache.logging.log4j:log4j-api" // "any" version must not introduce conficts
              )
              .withResolutionParams(params)
              .withCache(cache)
              .future()
          }

          await(validateDependencies(res, params))
        }
      }
    }

    test("dontBumpRootDependencies") {
      test {
        async {

          val params = ResolutionParams()
            .addRule(DontBumpRootDependencies(), RuleResolution.TryResolve)

          val res = await {
            Resolve()
              .noMirrors
              .addBoms0(dep"org.apache.logging.log4j:log4j-bom:2.23.1".moduleVersionConstraint)
              .addDependencies(
                dep"com.github.alexarchambault:argonaut-shapeless_6.2_2.12:1.2.0-M9",
                dep"com.chuusai:shapeless_2.12:2.3.2",
                dep"org.apache.logging.log4j:log4j-api" // "any" version substitution is not a bump
              )
              .withResolutionParams(params)
              .withCache(cache)
              .future()
          }

          val deps = res.dependenciesWithRetainedVersions

          val shapelessVersions = deps.collect {
            case dep if dep.module == mod"com.chuusai:shapeless_2.12" =>
              dep.versionConstraint
          }
          val expectedShapelessVersions = Set(VersionConstraint("2.3.2"))

          assert(shapelessVersions == expectedShapelessVersions)
        }
      }

      test {
        async {

          val params = ResolutionParams()
            .addRule(
              DontBumpRootDependencies(excl"org.scala-lang:scala-library"),
              RuleResolution.TryResolve
            )

          val res = await {
            Resolve()
              .noMirrors
              .addDependencies(
                dep"com.github.alexarchambault:argonaut-shapeless_6.2_2.12:1.2.0-M9",
                dep"com.chuusai:shapeless_2.12:2.3.2",
                dep"org.scala-lang:scala-library:2.12.1"
              )
              .withResolutionParams(params)
              .withCache(cache)
              .future()
          }

          val deps = res.dependenciesWithRetainedVersions

          val shapelessVersions = deps.collect {
            case dep if dep.module == mod"com.chuusai:shapeless_2.12" =>
              dep.versionConstraint
          }
          val expectedShapelessVersions = Set(VersionConstraint("2.3.2"))

          assert(shapelessVersions == expectedShapelessVersions)

          val scalaLibraryVersions = deps.collect {
            case dep if dep.module == mod"org.scala-lang:scala-library" =>
              dep.versionConstraint
          }
          val expectedScalaLibraryVersions = Set(VersionConstraint("2.12.6"))

          assert(scalaLibraryVersions == expectedScalaLibraryVersions)
        }
      }
    }
  }

}
