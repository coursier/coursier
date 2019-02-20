package coursier.rule.parser

import coursier.moduleString
import coursier.params.rule.{AlwaysFail, RuleResolution}
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
