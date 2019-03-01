package coursier

import coursier.error.conflict.{StrictRule, UnsatisfiedRule}
import coursier.params.ResolutionParams
import coursier.params.rule.{AlwaysFail, DontBumpRootDependencies, RuleResolution, SameVersion, Strict}
import coursier.util.{ModuleMatcher, ModuleMatchers, Repositories}
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

    'sameVersionRule - async {

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
          .addDependencies(dep"sh.almond:scala-kernel_2.12.7:0.2.2")
          .addRepositories(Repositories.jitpack)
          .withResolutionParams(params)
          .withCache(cache)
          .future()
      }

      await(validateDependencies(res, params))
    }

    'strict - {
      'fail - async {

        val rule = Strict
        val ruleRes = RuleResolution.Fail

        val params = ResolutionParams()
          .addRule(rule, ruleRes)

        val ex = await {
          Resolve()
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
            f.conflict.asInstanceOf[Strict.EvictedDependencies].evicted.foreach(println)
          case _ =>
            throw new Exception("Unexpected exception type", ex)
        }
      }
    }

    'dontBumpRootDependencies - {
      * - async {

        val params = ResolutionParams()
          .addRule(DontBumpRootDependencies(), RuleResolution.TryResolve)

        val res = await {
          Resolve()
            .addDependencies(
              dep"com.github.alexarchambault:argonaut-shapeless_6.2_2.12:1.2.0-M9",
              dep"com.chuusai:shapeless_2.12:2.3.2"
            )
            .withResolutionParams(params)
            .withCache(cache)
            .future()
        }

        val deps = res.dependenciesWithSelectedVersions

        val shapelessVersions = deps.collect {
          case dep if dep.module == mod"com.chuusai:shapeless_2.12" =>
            dep.version
        }
        val expectedShapelessVersions = Set("2.3.2")

        assert(shapelessVersions == expectedShapelessVersions)
      }

      * - async {

        val params = ResolutionParams()
          .addRule(DontBumpRootDependencies(
            ModuleMatchers(
              Set(ModuleMatcher(mod"org.scala-lang:scala-library"))
            )
          ), RuleResolution.TryResolve)

        val res = await {
          Resolve()
            .addDependencies(
              dep"com.github.alexarchambault:argonaut-shapeless_6.2_2.12:1.2.0-M9",
              dep"com.chuusai:shapeless_2.12:2.3.2",
              dep"org.scala-lang:scala-library:2.12.1"
            )
            .withResolutionParams(params)
            .withCache(cache)
            .future()
        }

        val deps = res.dependenciesWithSelectedVersions

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
