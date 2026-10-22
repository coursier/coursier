package coursier.core

import utest._

object PropertyExprTests extends TestSuite {

  private def substitute(
    value: String,
    properties: Map[String, String],
    trim: Boolean = false
  ): String =
    PropertyExpr.parse(value).substitute(PropertyValueLookup.fromMap(properties), trim)

  private def doublingChain(length: Int, first: String): Map[String, String] =
    (1 to length).map(i => s"p$i" -> s"$${p${i - 1}}$${p${i - 1}}").toMap +
      ("p0" -> first)

  private def linearChain(length: Int): Map[String, String] =
    (1 to length).map(i => s"p$i" -> s"$${p${i - 1}}").toMap +
      ("p0" -> "value")

  val tests = Tests {

    test("doublingChain") {
      test("small") {
        val res = substitute(s"$${p10}", doublingChain(10, "ab"))
        assert(res == "ab" * 1024)
      }
      test("emptyValues") {
        // Takes exponential time without memoization, even though the result is empty
        val res = substitute(s"a$${p60}b", doublingChain(60, ""))
        assert(res == "ab")
      }
      test("tooLarge") {
        val res = substitute(s"$${p40}", doublingChain(40, "x"))
        assert(res == s"$${p40}")
      }
      test("tooLargeComposite") {
        val res = substitute(s"a-$${p40}-b", doublingChain(40, "x"))
        assert(res == s"a-$${p40}-b")
      }
      test("tooLargeMemoized") {
        // Every memoized value is below the limit, but not their sum
        val half  = PropertyExpr.MaxExpandedLength / 2 + 1
        val props = Map("big" -> ("x" * half)) ++
          (1 to 3).map(i => s"q$i" -> s"$${big}$i").toMap ++
          Map("all" -> s"$${q1}$${q2}$${q3}")
        val res = substitute(s"$${all}", props)
        assert(res == s"$${all}")
      }
    }

    test("linearChain") {
      test("long") {
        val length = PropertyExpr.MaxDepth - 10
        val res    = substitute(s"$${p$length}", linearChain(length))
        assert(res == "value")
      }
      test("longComposite") {
        val length = PropertyExpr.MaxDepth - 10
        val props  = (1 to length).map(i => s"p$i" -> s"$${p${i - 1}}-").toMap +
          ("p0" -> "value")
        val res = substitute(s"$${p$length}", props)
        assert(res == "value" + "-" * length)
      }
      test("tooDeep") {
        val length = 100000
        val res    = substitute(s"a-$${p$length}-b", linearChain(length))
        assert(res == s"a-$${p$length}-b")
      }
    }

    test("cycle") {
      test("reference") {
        val res = substitute(s"$${a}", Map("a" -> s"$${b}", "b" -> s"$${a}"))
        assert(res == s"$${a}")
      }
      test("composite") {
        val res = substitute(s"x-$${a}-y", Map("a" -> s"1$${b}", "b" -> s"2$${a}"))
        assert(res == s"x-$${a}-y")
      }
      test("self") {
        val res = substitute(s"$${a}", Map("a" -> s"$${a}"))
        assert(res == s"$${a}")
      }
    }

    test("memoizationKeepsTrimming") {
      val props = Map(
        "a" -> " 1 ",
        "b" -> s" $${a} ",
        "c" -> s"$${b}-$${b}"
      )
      assert(substitute(s"$${c}", props, trim = true) == "1-1")
      assert(substitute(s"$${c}", props, trim = false) == "  1  -  1  ")
    }
  }
}
