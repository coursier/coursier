package coursier.rule.parser

import coursier.moduleString
import coursier.params.rule.{AlwaysFail, RuleResolution, SameVersion}
import utest._

object ShortParserTests extends TestSuite {

  val tests = Tests {

    'rule - {

      'alwaysFail - {

        'ok - {
          val s = "AlwaysFail"
          val expectedRes = Right((AlwaysFail(), RuleResolution.TryResolve))
          val res = ShortParser.parseRule(s)
          assert(res == expectedRes)
        }

        'trailingChars - {
          val s = "AlwaysFailz"
          val res = ShortParser.parseRule(s)
          assert(res.isLeft)
        }

      }

      'sameVersion - {

        * - {
          val s = "SameVersion(com.michael:jackson-core)"
          val expectedRes = Right((SameVersion(mod"com.michael:jackson-core"), RuleResolution.TryResolve))
          val res = ShortParser.parseRule(s)
          assert(res == expectedRes)
        }

        * - {
          val s = "SameVersion()"
          val res = ShortParser.parseRule(s)
          assert(res.isLeft)
        }

        * - {
          val s = "SameVersion(com.michael:jackson-core, com.michael:jackson-databind)"
          val expectedRes = Right((SameVersion(mod"com.michael:jackson-core", mod"com.michael:jackson-databind"), RuleResolution.TryResolve))
          val res = ShortParser.parseRule(s)
          assert(res == expectedRes)
        }

        * - {
          val s = "SameVersion(com.michael:jackson-core,com.michael:jackson-databind)"
          val expectedRes = Right((SameVersion(mod"com.michael:jackson-core", mod"com.michael:jackson-databind"), RuleResolution.TryResolve))
          val res = ShortParser.parseRule(s)
          assert(res == expectedRes)
        }

      }

    }

    'explicitRuleResolution - {

      'resolve - {
        val s = "resolve:SameVersion(com.michael:jackson-core)"
        val expectedRes = Right((SameVersion(mod"com.michael:jackson-core"), RuleResolution.TryResolve))
        val res = ShortParser.parseRule(s)
        assert(res == expectedRes)
      }

      'fail - {
        val s = "fail:SameVersion(com.michael:jackson-core)"
        val expectedRes = Right((SameVersion(mod"com.michael:jackson-core"), RuleResolution.Fail))
        val res = ShortParser.parseRule(s)
        assert(res == expectedRes)
      }

      'warn - {
        val s = "warn:SameVersion(com.michael:jackson-core)"
        val expectedRes = Right((SameVersion(mod"com.michael:jackson-core"), RuleResolution.Warn))
        val res = ShortParser.parseRule(s)
        assert(res == expectedRes)
      }

    }

    'rules - {

      * - {
        val s = "AlwaysFail"
        val expectedRes = Right(Seq((AlwaysFail(), RuleResolution.TryResolve)))
        val res = ShortParser.parseRules(s)
        assert(res == expectedRes)
      }

      * - {
        val s = "AlwaysFail, AlwaysFail"
        val expectedRes = Right(Seq(
          AlwaysFail(),
          AlwaysFail()
        ).map(_ -> RuleResolution.TryResolve))
        val res = ShortParser.parseRules(s)
        assert(res == expectedRes)
      }

      'trailingCharacters - {
        * - {
          val s = "AlwaysFail, AlwaysFailzzz"
          val res = ShortParser.parseRules(s)
          assert(res.isLeft)
        }

        * - {
          val s = "AlwaysFail, AlwaysFail zzz"
          val res = ShortParser.parseRules(s)
          assert(res.isLeft)
        }
      }

    }

  }

}
