package coursier

import coursier.core.Reconciliation
import coursier.error.conflict.{StrictRule, UnsatisfiedRule}
import coursier.graph.Conflict
import coursier.params.ResolutionParams
import coursier.params.rule.{AlwaysFail, DontBumpRootDependencies, RuleResolution, SameVersion, Strict}
import coursier.util.{ModuleMatcher, ModuleMatchers}
import utest._

import scala.async.Async.{async, await}

object ResolveRulesTests extends TestSuite {

  import TestHelpers.{ec, cache, validateDependencies}

  val tests = Tests {

    'alwaysFail - {
      'wrongRuleTryResolve - async {

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

      'failRuleTryResolve - async {

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

      'failRuleResolution - async {

        val rule = AlwaysFail()
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

    'sameVersionRule - {
      * - async {

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

      * - async {

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

    'strict - {
      'fail - async {

        val rule = Strict()
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

      "for roots" - async {

        val rule = Strict()
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
          Conflict(mod"org.typelevel:cats-core_2.11", "1.6.0", "1.5.0", wasExcluded = false, mod"org.typelevel:cats-core_2.11", "1.5.0")
        )
        val evicted = ex match {
          case f: StrictRule =>
            assert(f.rule == rule)
            assert(f.conflict.isInstanceOf[Strict.EvictedDependencies])
            f.conflict match {
              case e: Strict.EvictedDependencies => e.evicted.map(_.conflict)
              case _ => ???
            }
          case _ =>
            throw new Exception("Unexpected exception type", ex)
        }

        assert(evicted == expectedEvicted)
      }

      "with intervals" - async {

        val rule = Strict(
          exclude = Set(
            ModuleMatcher(mod"org.scala-lang:*")
          )
        )
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
          Conflict(mod"com.chuusai:shapeless_2.12", "2.3.3", "2.3.2", wasExcluded = false, mod"com.github.alexarchambault:argonaut-shapeless_6.2_2.12", "1.2.0-M4")
        )
        val evicted = ex match {
          case f: StrictRule =>
            assert(f.rule == rule)
            assert(f.conflict.isInstanceOf[Strict.EvictedDependencies])
            f.conflict match {
              case e: Strict.EvictedDependencies => e.evicted.map(_.conflict)
              case _ => ???
            }
          case _ =>
            throw new Exception("Unexpected exception type", ex)
        }

        assert(evicted == expectedEvicted)
      }

      "ignore if forced version" - {
        "do ignore" - async {

          val rule = Strict(
            exclude = Set(
              ModuleMatcher(mod"org.scala-lang:*")
            )
          )
          val ruleRes = RuleResolution.Fail

          val params = ResolutionParams()
            .addRule(rule, ruleRes)
            .addForceVersion(mod"com.chuusai:shapeless_2.12" -> "2.3.3")

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

        "do not ignore" - async {

          val rule = Strict(
            exclude = Set(
              ModuleMatcher(mod"org.scala-lang:*")
            ),
            ignoreIfForcedVersion = false
          )
          val ruleRes = RuleResolution.Fail

          val params = ResolutionParams()
            .addRule(rule, ruleRes)
            .addForceVersion(mod"com.chuusai:shapeless_2.12" -> "2.3.3")

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
            Conflict(mod"com.chuusai:shapeless_2.12", "2.3.3", "2.3.2", wasExcluded = false, mod"com.github.alexarchambault:argonaut-shapeless_6.2_2.12", "1.2.0-M4")
          )
          val evicted = ex match {
            case f: StrictRule =>
              assert(f.rule == rule)
              assert(f.conflict.isInstanceOf[Strict.EvictedDependencies])
              f.conflict match {
                case e: Strict.EvictedDependencies => e.evicted.map(_.conflict)
                case _ => ???
              }
            case _ =>
              throw new Exception("Unexpected exception type", ex)
          }

          assert(evicted == expectedEvicted)
        }
      }

      'viaReconciliation - async {

        val params = ResolutionParams()
          .addReconciliation(ModuleMatchers.all -> Reconciliation.Strict)

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

    "semVer reconciliation" - {
      "strict check" - async {

        val params = ResolutionParams()
          .addReconciliation(ModuleMatchers.all -> Reconciliation.Strict)

        val ex = await {
          Resolve()
            .noMirrors
            .addDependencies(
              dep"com.github.alexarchambault:argonaut-shapeless_6.2_2.11:1.2.0-M11",
              dep"io.argonaut:argonaut_2.11:6.1"
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

      "conflict" - async {

        val params = ResolutionParams()
          .addReconciliation(ModuleMatchers.all -> Reconciliation.SemVer)

        val ex = await {
          Resolve()
            .noMirrors
            .addDependencies(
              dep"com.github.alexarchambault:argonaut-shapeless_6.2_2.11:1.2.0-M11",
              dep"io.argonaut:argonaut_2.11:6.1"
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
              "6.2.3",
              "6.1",
              wasExcluded = false,
              mod"io.argonaut:argonaut_2.11",
              "6.1"
            )
            assert(conflict == expectedConflict)
          case _ =>
            throw new Exception("Unexpected exception type", ex)
        }
      }

      "no conflict" - async {

        val params = ResolutionParams()
          .addReconciliation(ModuleMatchers.all -> Reconciliation.SemVer)

        val res = await {
          Resolve()
            .noMirrors
            .addDependencies(
              dep"com.github.alexarchambault:argonaut-shapeless_6.2_2.11:1.2.0-M11",
              dep"io.argonaut:argonaut_2.11:6.2"
            )
            .withResolutionParams(params)
            .withCache(cache)
            .future()
        }

        await(validateDependencies(res, params))
      }
    }

    'dontBumpRootDependencies - {
      * - async {

        val params = ResolutionParams()
          .addRule(DontBumpRootDependencies(), RuleResolution.TryResolve)

        val res = await {
          Resolve()
            .noMirrors
            .addDependencies(
              dep"com.github.alexarchambault:argonaut-shapeless_6.2_2.12:1.2.0-M9",
              dep"com.chuusai:shapeless_2.12:2.3.2"
            )
            .withResolutionParams(params)
            .withCache(cache)
            .future()
        }

        val deps = res.dependenciesWithRetainedVersions

        val shapelessVersions = deps.collect {
          case dep if dep.module == mod"com.chuusai:shapeless_2.12" =>
            dep.version
        }
        val expectedShapelessVersions = Set("2.3.2")

        assert(shapelessVersions == expectedShapelessVersions)
      }

      * - async {

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
            dep.version
        }
        val expectedShapelessVersions = Set("2.3.2")

        assert(shapelessVersions == expectedShapelessVersions)

        val scalaLibraryVersions = deps.collect {
          case dep if dep.module == mod"org.scala-lang:scala-library" =>
            dep.version
        }
        val expectedScalaLibraryVersions = Set("2.12.6")

        assert(scalaLibraryVersions == expectedScalaLibraryVersions)
      }
    }
  }

}
