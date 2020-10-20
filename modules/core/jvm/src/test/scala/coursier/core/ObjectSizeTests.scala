package coursier.core

import utest._

object ObjectSizeTests extends TestSuite {

  val tests = Tests {

    test("Dependency sizes") {
      test("should be the same for same dependency") {
        def d = Dependency(Module(Organization("tpolecat"), ModuleName("doobie-core_2.12"), Map.empty), "0.6.0")
        assert(d == d)
        assert(d eq d)
        assert(d.hashCode == d.hashCode)
        assert(size(Array(d))  == size(Array(d,d)))
      }

      test("should be the different for different dependency") {
        def d1 = Dependency(Module(Organization("tpolecat"), ModuleName("doobie-core_2.12"), Map.empty), "0.6.0")
        def d2 = Dependency(Module(Organization("tpolecat"), ModuleName("doobie-core_2.12"), Map.empty), "0.7.0")
        assert(size(Array(d1)) <= size(Array(d1,d2)))
      }
    }

    test("Module sizes") {
      test("should be the same for same Module") {
        def m = Module(Organization("tpolecat"), ModuleName("doobie-core_2.12"), Map.empty)
        assert(m == m)
        assert(m eq m)
        assert(m.hashCode == m.hashCode)
        assert(size(Array(m))  == size(Array(m,m)))
      }

      test("should be the different for different Module") {
        def m1 = Module(Organization("tpolecat"), ModuleName("doobie-core_2.12"), Map.empty)
        def m2 = Module(Organization("tpolecat"), ModuleName("doobie-core_2.13"), Map.empty)
        assert(size(Array(m1)) <= size(Array(m1,m2)))
      }
    }

    test("Publication sizes") {
      test("should be the same for same Publication") {
        def m = Publication("a", Type.jar, Extension.jar, Classifier.empty)
        assert(m == m)
        assert(m eq m)
        assert(m.hashCode == m.hashCode)
        assert(size(Array(m))  == size(Array(m,m)))
      }

      test("should be the different for different Publication") {
        def m1 = Publication("a", Type.jar, Extension.jar, Classifier.empty)
        def m2 = Publication("a", Type.jar, Extension.jar, Classifier.tests)
        assert(size(Array(m1)) <= size(Array(m1,m2)))
      }
    }

  }

  def size(a: Object) = {
    val g = org.openjdk.jol.info.GraphLayout.parseInstance(a)
    g.totalSize()
  }

}
