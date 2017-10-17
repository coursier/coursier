package coursier.util

import utest._

object TreeTests extends TestSuite {

  case class Node(label: String, children: Node*)

  val roots = Array(
    Node("p1",
      Node("c1"),
      Node("c2")),
    Node("p2",
      Node("c3"),
      Node("c4"))
  )

  val moreNestedRoots = Array(
    Node("p1",
      Node("c1",
        Node("p2"))),
    Node("p2",
      Node("c1"))
  )


  val tests = TestSuite {
    'apply {
      val str = Tree[Node](roots)(_.children, _.label)
      assert(str ==
        """├─ p1
        #│  ├─ c1
        #│  └─ c2
        #└─ p2
        #   ├─ c3
        #   └─ c4""".stripMargin('#'))
    }

    'apply {
      val str = Tree[Node](moreNestedRoots)(_.children, _.label)
      println(str)
      assert(str ==
        """├─ p1
        #│  └─ c1
        #│     └─ p2
        #└─ p2
        #   └─ c1""".stripMargin('#'))
    }
  }
}
