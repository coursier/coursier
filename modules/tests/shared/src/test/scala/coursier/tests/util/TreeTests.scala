package coursier.tests.util

import coursier.util.Tree
import utest._

import scala.collection.mutable.ArrayBuffer

object TreeTests extends TestSuite {

  private final case class MutableTree(label: String, children: ArrayBuffer[MutableTree]) {
    def addChild(x: MutableTree): Unit =
      children.append(x)

    // The default behavior of hashcode will calculate things recursively,
    // which will be infinite because we want to test cycles, so hardcoding
    // the hashcode to 0 to get around the issue.
    // TODO: make the hashcode to return something more interesting to
    // improve performance.
    override def hashCode(): Int = label.##
  }

  private val roots = Vector(
    MutableTree(
      "p1",
      ArrayBuffer(
        MutableTree("c1", ArrayBuffer.empty),
        MutableTree("c2", ArrayBuffer.empty)
      )
    ),
    MutableTree(
      "p2",
      ArrayBuffer(
        MutableTree("c3", ArrayBuffer.empty),
        MutableTree("c4", ArrayBuffer.empty)
      )
    )
  )

  private val moreNestedRoots = Vector(
    MutableTree(
      "p1",
      ArrayBuffer(
        MutableTree(
          "c1",
          ArrayBuffer(
            MutableTree("p2", ArrayBuffer.empty)
          )
        )
      )
    ),
    MutableTree(
      "p3",
      ArrayBuffer(
        MutableTree("d1", ArrayBuffer.empty)
      )
    )
  )

  // Constructing cyclic graph:
  // a -> b -> c -> a
  //             -> e -> f

  private val a = MutableTree("a", ArrayBuffer.empty)
  private val b = MutableTree("b", ArrayBuffer.empty)
  private val c = MutableTree("c", ArrayBuffer.empty)
  private val e = MutableTree("e", ArrayBuffer.empty)
  private val f = MutableTree("f", ArrayBuffer.empty)

  a.addChild(b)
  b.addChild(c)
  c.addChild(a)
  c.addChild(e)
  e.addChild(f)

  val tests = Tests {
    /** Verifies the `basic` scenario behaves as the user expects. */
    test("basic") {
      val str = Tree[MutableTree](roots)(_.children.toSeq)
        .render(_.label)
      assert(str.replace("\r\n", "\n") ==
        """├─ p1
          #│  ├─ c1
          #│  └─ c2
          #└─ p2
          #   ├─ c3
          #   └─ c4""".stripMargin('#'))
    }

    /** Verifies the `moreNested` scenario behaves as the user expects. */
    test("moreNested") {
      val str = Tree[MutableTree](moreNestedRoots)(_.children.toSeq)
        .render(_.label)
      assert(str.replace("\r\n", "\n") ==
        """├─ p1
          #│  └─ c1
          #│     └─ p2
          #└─ p3
          #   └─ d1""".stripMargin('#'))
    }

    /** Verifies the `cyclic1` scenario behaves as the user expects. */
    test("cyclic1") {
      val str: String = Tree[MutableTree](Vector(a, e))(_.children.toSeq)
        .render(_.label)
      assert(str.replace("\r\n", "\n") ==
        """├─ a
          #│  └─ b
          #│     └─ c
          #│        └─ e
          #│           └─ f
          #└─ e
          #   └─ f""".stripMargin('#'))
    }

    /** Verifies the `cyclic2` scenario behaves as the user expects. */
    test("cyclic2") {
      val str: String = Tree[MutableTree](Vector(a, c))(_.children.toSeq)
        .render(_.label)
      assert(str.replace("\r\n", "\n") ==
        """├─ a
          #│  └─ b
          #│     └─ c
          #│        └─ e
          #│           └─ f
          #└─ c
          #   ├─ a
          #   │  └─ b
          #   └─ e
          #      └─ f""".stripMargin('#'))
    }
  }
}
