package coursier.parse

import coursier.moduleString
import coursier.params.rule._
import coursier.util.{ModuleMatcher, ModuleMatchers}
import utest._

object JsonRuleParserTests extends TestSuite {

  val tests = Tests {

    test("rule") {
      test("alwaysFail") {

        test("simple") {
          val rule =
            """{
              |  "rule": "always-fail"
              |}
            """.stripMargin
          val res = JsonRuleParser.parseRule(rule, "2.12.8")
          val expectedRes = Right((AlwaysFail(), RuleResolution.TryResolve))
          assert(res == expectedRes)
        }

        test("defaultAction") {
          val rule =
            """{
              |  "rule": "always-fail"
              |}
            """.stripMargin
          val action = RuleResolution.Warn
          val res = JsonRuleParser.parseRule(rule, "2.12.8", action)
          val expectedRes = Right((AlwaysFail(), action))
          assert(res == expectedRes)
        }

      }

      test("sameVersion") {

        test {
          val rule =
            """{
              |  "rule": "same-version",
              |  "modules": ["com.fasterxml.jackson.core:jackson-*"]
              |}
            """.stripMargin
          val res = JsonRuleParser.parseRule(rule, "2.12.8")
          val expectedRes = Right((SameVersion(mod"com.fasterxml.jackson.core:jackson-*"), RuleResolution.TryResolve))
          assert(res == expectedRes)
        }

        test {
          val rule =
            """{
              |  "rule": "same-version",
              |  "modules": [
              |    "com.fasterxml.jackson.core:jackson-core",
              |    "com.fasterxml.jackson.core:jackson-databind"
              |  ]
              |}
            """.stripMargin
          val res = JsonRuleParser.parseRule(rule, "2.12.8")
          val expectedRes = Right((SameVersion(
            mod"com.fasterxml.jackson.core:jackson-core",
            mod"com.fasterxml.jackson.core:jackson-databind"
          ), RuleResolution.TryResolve))
          assert(res == expectedRes)
        }

      }

      test("dontBumpRootDependencies") {

        test {
          val rule =
            """{
              |  "rule": "dont-bump-root-dependencies"
              |}
            """.stripMargin
          val res = JsonRuleParser.parseRule(rule, "2.12.8")
          val expectedRes = Right((DontBumpRootDependencies(), RuleResolution.TryResolve))
          assert(res == expectedRes)
        }

        test {
          val rule =
            """{
              |  "rule": "dont-bump-root-dependencies",
              |  "exclude": [
              |    "org.scala-lang:*"
              |  ]
              |}
            """.stripMargin
          val res = JsonRuleParser.parseRule(rule, "2.12.8")
          val expectedRes = Right((DontBumpRootDependencies(
            ModuleMatchers(
              Set(ModuleMatcher(mod"org.scala-lang:*")),
              Set()
            )
          ), RuleResolution.TryResolve))
          assert(res == expectedRes)
        }

        test {
          val rule =
            """{
              |  "rule": "dont-bump-root-dependencies",
              |  "exclude": [
              |    "org.scala-lang:*"
              |  ],
              |  "include": [
              |    "org.scala-lang:scala-library"
              |  ]
              |}
            """.stripMargin
          val res = JsonRuleParser.parseRule(rule, "2.12.8")
          val expectedRes = Right((DontBumpRootDependencies(
            ModuleMatchers(
              Set(ModuleMatcher(mod"org.scala-lang:*")),
              Set(ModuleMatcher(mod"org.scala-lang:scala-library"))
            )
          ), RuleResolution.TryResolve))
          assert(res == expectedRes)
        }

      }

      test("strict") {

        test("simple") {
          test {
            val rule =
              """{
                |  "rule": "strict"
                |}
              """.stripMargin
            val res = JsonRuleParser.parseRule(rule, "2.12.8")
            val expectedRes = Right((Strict(), RuleResolution.TryResolve))
            assert(res == expectedRes)
          }

          test {
            val rule =
              """{
                |  "rule": "strict",
                |  "include": [
                |    "org:*"
                |  ]
                |}
              """.stripMargin
            val res = JsonRuleParser.parseRule(rule, "2.12.8")
            val expectedRes = Right((Strict(Set(ModuleMatcher(mod"org:*"))), RuleResolution.TryResolve))
            assert(res == expectedRes)
          }
        }

        test("exclude") {
          test {
            val rule =
              """{
                |  "rule": "strict",
                |  "include": [
                |    "org:*"
                |  ],
                |  "exclude": [
                |    "org:name",
                |    "org:foo"
                |  ]
                |}
              """.stripMargin
            val res = JsonRuleParser.parseRule(rule, "2.12.8")
            val expectedRes = Right((Strict(Set(ModuleMatcher(mod"org:*")), Set(ModuleMatcher(mod"org:name"), ModuleMatcher(mod"org:foo"))), RuleResolution.TryResolve))
            assert(res == expectedRes)
          }
        }

        test("defaultAction") {
          val rule =
            """{
              |  "rule": "strict"
              |}
            """.stripMargin
          val action = RuleResolution.Warn
          val res = JsonRuleParser.parseRule(rule, "2.12.8", action)
          val expectedRes = Right((Strict(), action))
          assert(res == expectedRes)
        }

      }
    }

    test("rules") {
      test("empty") {
        val rules = "[]"
        val res = JsonRuleParser.parseRules(rules, "2.12.8")
        val expectedRes = Right(Nil)
        assert(res == expectedRes)
      }

      test("one") {

        test {
          val rules =
            """[
              |  {
              |    "rule": "always-fail"
              |  }
              |]
            """.stripMargin
          val res = JsonRuleParser.parseRules(rules, "2.12.8")
          val expectedRes = Right(Seq((AlwaysFail(), RuleResolution.TryResolve)))
          assert(res == expectedRes)
        }

        test {
          val rules =
            """[
              |  {
              |    "rule": "always-fail",
              |    "action": "fail"
              |  }
              |]
            """.stripMargin
          val res = JsonRuleParser.parseRules(rules, "2.12.8")
          val expectedRes = Right(Seq((AlwaysFail(), RuleResolution.Fail)))
          assert(res == expectedRes)
        }

      }

      test("two") {

        test {
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
          val res = JsonRuleParser.parseRules(rules, "2.12.8")
          val expectedRes = Right(Seq(
            (AlwaysFail(), RuleResolution.TryResolve),
            (AlwaysFail(), RuleResolution.TryResolve)
          ))
          assert(res == expectedRes)
        }

        test {
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
          val res = JsonRuleParser.parseRules(rules, "2.12.8")
          val expectedRes = Right(Seq(
            (AlwaysFail(), RuleResolution.TryResolve),
            (SameVersion(mod"com.fasterxml.jackson.core:jackson-*"), RuleResolution.Warn)
          ))
          assert(res == expectedRes)
        }

        test {
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
          val res = JsonRuleParser.parseRules(rules, "2.12.8")
          val expectedRes = Right(Seq(
            (DontBumpRootDependencies(), RuleResolution.Fail),
            (SameVersion(mod"com.fasterxml.jackson.core:jackson-*"), RuleResolution.Warn)
          ))
          assert(res == expectedRes)
        }

      }
    }

  }

}
