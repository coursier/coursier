package coursier.rule.parser

import coursier.params.rule.{AlwaysFail, RuleResolution}
import utest._

object JsonParserTests extends TestSuite {

  val tests = Tests {

    'rule - {
      'alwaysFail - {

        'simple - {
          val rule =
            """{
              |  "rule": "always-fail"
              |}
            """.stripMargin
          val res = JsonParser.parseRule(rule, "2.12.8")
          val expectedRes = Right((AlwaysFail(), RuleResolution.TryResolve))
          assert(res == expectedRes)
        }

        'defaultAction - {
          val rule =
            """{
              |  "rule": "always-fail"
              |}
            """.stripMargin
          val action = RuleResolution.Warn
          val res = JsonParser.parseRule(rule, "2.12.8", action)
          val expectedRes = Right((AlwaysFail(), action))
          assert(res == expectedRes)
        }

      }
    }

    'rules - {
      'empty - {
        val rules = "[]"
        val res = JsonParser.parseRules(rules, "2.12.8")
        val expectedRes = Right(Nil)
        assert(res == expectedRes)
      }

      'one - {

        * - {
          val rules =
            """[
              |  {
              |    "rule": "always-fail"
              |  }
              |]
            """.stripMargin
          val res = JsonParser.parseRules(rules, "2.12.8")
          val expectedRes = Right(Seq((AlwaysFail(), RuleResolution.TryResolve)))
          assert(res == expectedRes)
        }

        * - {
          val rules =
            """[
              |  {
              |    "rule": "always-fail",
              |    "action": "fail"
              |  }
              |]
            """.stripMargin
          val res = JsonParser.parseRules(rules, "2.12.8")
          val expectedRes = Right(Seq((AlwaysFail(), RuleResolution.Fail)))
          assert(res == expectedRes)
        }

      }

      'two - {

        * - {
          val rules =
            """[
              |  {
              |    "rule": "always-fail"
              |  },
              |  {
              |    "rule": "always-fail"
              |  }
              |]
            """.stripMargin
          val res = JsonParser.parseRules(rules, "2.12.8")
          val expectedRes = Right(Seq(
            (AlwaysFail(), RuleResolution.TryResolve),
            (AlwaysFail(), RuleResolution.TryResolve)
          ))
          assert(res == expectedRes)
        }

      }
    }

  }

}
