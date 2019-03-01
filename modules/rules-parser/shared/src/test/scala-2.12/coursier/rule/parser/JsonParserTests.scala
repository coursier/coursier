package coursier.rule.parser

import coursier.moduleString
import coursier.params.rule.{AlwaysFail, DontBumpRootDependencies, RuleResolution, SameVersion}
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

      'sameVersion - {

        * - {
          val rule =
            """{
              |  "rule": "same-version",
              |  "modules": ["com.fasterxml.jackson.core:jackson-*"]
              |}
            """.stripMargin
          val res = JsonParser.parseRule(rule, "2.12.8")
          val expectedRes = Right((SameVersion(mod"com.fasterxml.jackson.core:jackson-*"), RuleResolution.TryResolve))
          assert(res == expectedRes)
        }

        * - {
          val rule =
            """{
              |  "rule": "same-version",
              |  "modules": [
              |    "com.fasterxml.jackson.core:jackson-core",
              |    "com.fasterxml.jackson.core:jackson-databind"
              |  ]
              |}
            """.stripMargin
          val res = JsonParser.parseRule(rule, "2.12.8")
          val expectedRes = Right((SameVersion(
            mod"com.fasterxml.jackson.core:jackson-core",
            mod"com.fasterxml.jackson.core:jackson-databind"
          ), RuleResolution.TryResolve))
          assert(res == expectedRes)
        }

      }

      'dontBumpRootDependencies - {

        * - {
          val rule =
            """{
              |  "rule": "dont-bump-root-dependencies"
              |}
            """.stripMargin
          val res = JsonParser.parseRule(rule, "2.12.8")
          val expectedRes = Right((DontBumpRootDependencies, RuleResolution.TryResolve))
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

        * - {
          val rules =
            """[
              |  {
              |    "rule": "always-fail"
              |  },
              |  {
              |    "rule": "same-version",
              |    "modules": [
              |      "com.fasterxml.jackson.core:jackson-*"
              |    ],
              |    "action": "warn"
              |  }
              |]
            """.stripMargin
          val res = JsonParser.parseRules(rules, "2.12.8")
          val expectedRes = Right(Seq(
            (AlwaysFail(), RuleResolution.TryResolve),
            (SameVersion(mod"com.fasterxml.jackson.core:jackson-*"), RuleResolution.Warn)
          ))
          assert(res == expectedRes)
        }

        * - {
          val rules =
            """[
              |  {
              |    "rule": "dont-bump-root-dependencies",
              |    "action": "fail"
              |  },
              |  {
              |    "rule": "same-version",
              |    "modules": [
              |      "com.fasterxml.jackson.core:jackson-*"
              |    ],
              |    "action": "warn"
              |  }
              |]
            """.stripMargin
          val res = JsonParser.parseRules(rules, "2.12.8")
          val expectedRes = Right(Seq(
            (DontBumpRootDependencies, RuleResolution.Fail),
            (SameVersion(mod"com.fasterxml.jackson.core:jackson-*"), RuleResolution.Warn)
          ))
          assert(res == expectedRes)
        }

      }
    }

  }

}
