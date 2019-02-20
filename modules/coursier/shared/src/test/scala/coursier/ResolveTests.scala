package coursier

import coursier.error.conflict.{StrictRule, UnsatisfiedRule}
import coursier.params.ResolutionParams
import coursier.params.rule.{AlwaysFail, RuleResolution}
import coursier.util.Repositories
import utest._

import scala.async.Async.{async, await}

object ResolveTests extends TestSuite {

  import TestHelpers.{ec, cache, validateDependencies}


  val tests = Tests {
    'simple - async {

      val res = await {
        Resolve.resolveFuture(
          Seq(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8"),
          cache = cache
        )
      }

      await(validateDependencies(res))
    }

    'forceScalaVersion - async {

      val params = ResolutionParams()
        .withScalaVersion("2.12.7")

      val res = await {
        Resolve.resolveFuture(
          Seq(dep"sh.almond:scala-kernel_2.12.7:0.2.2"),
          repositories = Resolve.defaultRepositories ++ Seq(
            Repositories.jitpack
          ),
          params = params,
          cache = cache
        )
      }

      await(validateDependencies(res, params))
    }

    'rules - {

      'alwaysFail - {
        'wrongRuleTryResolve - async {

          val rule = AlwaysFail(doTryResolve = true)
          // should fail anyway (tryResolve of AlwaysFail does nothing)
          val ruleRes = RuleResolution.TryResolve

          val params = ResolutionParams()
            .addRule(rule, ruleRes)

          val ex = await {
            Resolve.resolveFuture(
              Seq(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8"),
              params = params,
              cache = cache
            ).failed
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
            Resolve.resolveFuture(
              Seq(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8"),
              params = params,
              cache = cache
            ).failed
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
            Resolve.resolveFuture(
              Seq(dep"io.get-coursier:coursier-cli_2.12:1.1.0-M8"),
              params = params,
              cache = cache
            ).failed
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
  }
}
