package coursier
package test

import utest._

object ExclusionsTests extends TestSuite {

  def exclusionsAdd(
    e1: Set[(Organization, ModuleName)],
    e2: Set[(Organization, ModuleName)]
  ): Set[(Organization, ModuleName)] =
    core.MinimizedExclusions(e1).join(core.MinimizedExclusions(e2)).toSet

  val tests = Tests {
    val e1 = Set((org"org1", name"name1"))
    val e2 = Set((org"org2", name"name2"))

    val enb = Set((org"org1", name"*"))
    val eob = Set((org"*", name"name1"))
    val eb  = Set((org"*", name"*"))

    test("add") {
      test("basicZero") {
        val result1l = exclusionsAdd(e1, Set.empty)
        val result1r = exclusionsAdd(Set.empty, e1)
        val result2l = exclusionsAdd(e2, Set.empty)
        val result2r = exclusionsAdd(Set.empty, e2)
        assert(result1l == e1)
        assert(result1r == e1)
        assert(result2l == e2)
        assert(result2r == e2)
      }
      test("basic") {
        val expected = e1 ++ e2
        val result12 = exclusionsAdd(e1, e2)
        val result21 = exclusionsAdd(e2, e1)
        assert(result12 == expected)
        assert(result21 == expected)
      }

      test("nameBlob") {
        val result1b = exclusionsAdd(e1, enb)
        val resultb1 = exclusionsAdd(enb, e1)
        val result2b = exclusionsAdd(e2, enb)
        val resultb2 = exclusionsAdd(enb, e2)
        assert(result1b == enb)
        assert(resultb1 == enb)
        assert(result2b == (e2 ++ enb))
        assert(resultb2 == (e2 ++ enb))
      }

      test("orgBlob") {
        val result1b = exclusionsAdd(e1, eob)
        val resultb1 = exclusionsAdd(eob, e1)
        val result2b = exclusionsAdd(e2, eob)
        val resultb2 = exclusionsAdd(eob, e2)
        assert(result1b == eob)
        assert(resultb1 == eob)
        assert(result2b == (e2 ++ eob))
        assert(resultb2 == (e2 ++ eob))
      }

      test("blob") {
        val result1b = exclusionsAdd(e1, eb)
        val resultb1 = exclusionsAdd(eb, e1)
        val result2b = exclusionsAdd(e2, eb)
        val resultb2 = exclusionsAdd(eb, e2)
        assert(result1b == eb)
        assert(resultb1 == eb)
        assert(result2b == eb)
        assert(resultb2 == eb)
      }
    }
  }

}
