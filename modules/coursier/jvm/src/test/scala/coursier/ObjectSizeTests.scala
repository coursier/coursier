package coursier

import utest._

object ObjectSizeTests extends TestSuite {

  val tests = Tests {

    test("Object sizes") {
      test("should be the same for same dependency") {
        val resolution = Resolve()
          .addDependencies(dep"org.apache.spark:spark-sql_2.12:2.4.0")
          .run()

        println(size(resolution))
        assert(true)
      }

    }

  }

  def size(a: Object) = {
    val g = org.openjdk.jol.info.GraphLayout.parseInstance(a)
    println(g.toFootprint())
    g.totalSize()
  }

}
