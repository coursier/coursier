package coursier.parse

import coursier.moduleString
import coursier.params.rule._
import coursier.util.{ModuleMatcher, ModuleMatchers}
import utest._

object RuleParserTests extends TestSuite {

  val tests = Tests {

    'rule - {

      'alwaysFail - {

        'ok - {
          val s = "AlwaysFail"
          val expectedRes = Right((AlwaysFail(), RuleResolution.TryResolve))
          val res = RuleParser.rule(s)
          assert(res == expectedRes)
        }

        'trailingChars - {
          val s = "AlwaysFailz"
          val res = RuleParser.rule(s)
          assert(res.isLeft)
        }

      }

      'sameVersion - {

        * - {
          val s = "SameVersion(com.michael:jackson-core)"
          val expectedRes = Right((SameVersion(mod"com.michael:jackson-core"), RuleResolution.TryResolve))
          val res = RuleParser.rule(s)
          assert(res == expectedRes)
        }

        * - {
          val s = "SameVersion()"
          val res = RuleParser.rule(s)
          assert(res.isLeft)
        }

        * - {
          val s = "SameVersion(com.michael:jackson-core, com.michael:jackson-databind)"
          val expectedRes = Right((SameVersion(mod"com.michael:jackson-core", mod"com.michael:jackson-databind"), RuleResolution.TryResolve))
          val res = RuleParser.rule(s)
          assert(res == expectedRes)
        }

        * - {
          val s = "SameVersion(com.michael:jackson-core,com.michael:jackson-databind)"
          val expectedRes = Right((SameVersion(mod"com.michael:jackson-core", mod"com.michael:jackson-databind"), RuleResolution.TryResolve))
          val res = RuleParser.rule(s)
          assert(res == expectedRes)
        }

        * - {
          val s = "SameVersion(com.michael:jackson-*)"
          val expectedRes = Right((SameVersion(mod"com.michael:jackson-*"), RuleResolution.TryResolve))
          val res = RuleParser.rule(s)
          assert(res == expectedRes)
        }

      }

      'strict - {

        'simple - {
          * - {
            val s = "Strict"
            val expectedRes = Right((Strict(), RuleResolution.TryResolve))
            val res = RuleParser.rule(s)
            assert(res == expectedRes)
          }

          * - {
            val s = "Strict()"
            val expectedRes = Right((Strict(), RuleResolution.TryResolve))
            val res = RuleParser.rule(s)
            assert(res == expectedRes)
          }

          * - {
            val s = "Strict(org:name)"
            val expectedRes = Right((Strict(Set(ModuleMatcher(mod"org:name"))), RuleResolution.TryResolve))
            val res = RuleParser.rule(s)
            assert(res == expectedRes)
          }
        }

        'excludes - {
          * - {
            val s = "Strict(org:*, !org:name, !org:foo)"
            val expectedRes = Right((Strict(Set(ModuleMatcher(mod"org:*")), Set(ModuleMatcher(mod"org:name"), ModuleMatcher(mod"org:foo"))), RuleResolution.TryResolve))
            val res = RuleParser.rule(s)
            assert(res == expectedRes)
          }
        }

      }

    }

    'explicitRuleResolution - {

      'resolve - {
        val s = "resolve:SameVersion(com.michael:jackson-core)"
        val expectedRes = Right((SameVersion(mod"com.michael:jackson-core"), RuleResolution.TryResolve))
        val res = RuleParser.rule(s)
        assert(res == expectedRes)
      }

      'fail - {
        val s = "fail:SameVersion(com.michael:jackson-core)"
        val expectedRes = Right((SameVersion(mod"com.michael:jackson-core"), RuleResolution.Fail))
        val res = RuleParser.rule(s)
        assert(res == expectedRes)
      }

      'warn - {
        val s = "warn:SameVersion(com.michael:jackson-core)"
        val expectedRes = Right((SameVersion(mod"com.michael:jackson-core"), RuleResolution.Warn))
        val res = RuleParser.rule(s)
        assert(res == expectedRes)
      }

    }

    'rules - {

      * - {
        val s = "AlwaysFail"
        val expectedRes = Right(Seq((AlwaysFail(), RuleResolution.TryResolve)))
        val res = RuleParser.rules(s)
        assert(res == expectedRes)
      }

      * - {
        val s = "AlwaysFail, AlwaysFail"
        val expectedRes = Right(Seq(
          AlwaysFail(),
          AlwaysFail()
        ).map(_ -> RuleResolution.TryResolve))
        val res = RuleParser.rules(s)
        assert(res == expectedRes)
      }

       * - {
         val s = "DontBumpRootDependencies"
         val expectedRes = Right(Seq((DontBumpRootDependencies(), RuleResolution.TryResolve)))
         val res = RuleParser.rules(s)
         assert(res == expectedRes)
       }

      * - {
        val s = "DontBumpRootDependencies()"
        val expectedRes = Right(Seq((DontBumpRootDependencies(), RuleResolution.TryResolve)))
        val res = RuleParser.rules(s)
        assert(res == expectedRes)
      }

      * - {
        val s = "DontBumpRootDependencies(exclude=[])"
        val expectedRes = Right(Seq((DontBumpRootDependencies(), RuleResolution.TryResolve)))
        val res = RuleParser.rules(s)
        assert(res == expectedRes)
      }

      * - {
        val s = "DontBumpRootDependencies(exclude=[], include=[])"
        val expectedRes = Right(Seq((DontBumpRootDependencies(), RuleResolution.TryResolve)))
        val res = RuleParser.rules(s)
        assert(res == expectedRes)
      }

      * - {
        val s = "DontBumpRootDependencies(exclude=[org.scala-lang:*], include=[])"
        val matchers = ModuleMatchers(
          Set(ModuleMatcher(mod"org.scala-lang:*"))
        )
        val expectedRes = Right(Seq((DontBumpRootDependencies(matchers), RuleResolution.TryResolve)))
        val res = RuleParser.rules(s)
        assert(res == expectedRes)
      }

      * - {
        val s = "DontBumpRootDependencies(exclude=[org.scala-lang:*], include=[org.scala-lang:scala-library])"
        val matchers = ModuleMatchers(
          Set(ModuleMatcher(mod"org.scala-lang:*")),
          Set(ModuleMatcher(mod"org.scala-lang:scala-library"))
        )
        val expectedRes = Right(Seq((DontBumpRootDependencies(matchers), RuleResolution.TryResolve)))
        val res = RuleParser.rules(s)
        assert(res == expectedRes)
      }

       * - {
         val s = "DontBumpRootDependencies, SameVersion(com.michael:jackson-core)"
         val expectedRes = Right(Seq(
           DontBumpRootDependencies(),
           SameVersion(mod"com.michael:jackson-core")
         ).map(_ -> RuleResolution.TryResolve))
         val res = RuleParser.rules(s)
         assert(res == expectedRes)
       }

       * - {
         val s = "DontBumpRootDependencies, SameVersion(com.michael:jackson-core, com.michael:jackson-databind)"
         val expectedRes = Right(Seq(
           DontBumpRootDependencies(),
           SameVersion(mod"com.michael:jackson-core", mod"com.michael:jackson-databind")
         ).map(_ -> RuleResolution.TryResolve))
         val res = RuleParser.rules(s)
         assert(res == expectedRes)
       }

       * - {
         val s = "DontBumpRootDependencies, SameVersion(com.michael:jackson-core), SameVersion(com.a:b,com.a:c,com.a:d)"
         val expectedRes = Right(Seq(
           DontBumpRootDependencies(),
           SameVersion(mod"com.michael:jackson-core"),
           SameVersion(mod"com.a:b", mod"com.a:c", mod"com.a:d")
         ).map(_ -> RuleResolution.TryResolve))
         val res = RuleParser.rules(s)
         assert(res == expectedRes)
       }

      'trailingCharacters - {
        * - {
          val s = "AlwaysFail, AlwaysFailzzz"
          val res = RuleParser.rules(s)
          assert(res.isLeft)
        }

        * - {
          val s = "AlwaysFail, AlwaysFail zzz"
          val res = RuleParser.rules(s)
          assert(res.isLeft)
        }

        * - {
          val s = "DontBumpRootDependencies, SameVersion(com.michael:jackson-core)zzz"
          val res = RuleParser.rules(s)
          assert(res.isLeft)
        }
      }

    }

  }

}
