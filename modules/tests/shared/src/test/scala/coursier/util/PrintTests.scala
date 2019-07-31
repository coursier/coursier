package coursier.util

import coursier.core.{Attributes, Classifier, Configuration, Type}
import coursier.test.TestRunner
import coursier.graph.ReverseModuleTree
import coursier.{Dependency, moduleString}
import utest._

import scala.concurrent.ExecutionContext.Implicits.global

object PrintTests extends TestSuite {

  object AppliedTree {
    def apply[A](tree: Tree[A]): Seq[AppliedTree[A]] = {
      tree.roots.map(root => {
        AppliedTree[A](root, apply(Tree(tree.children(root).toIndexedSeq)(tree.children)))
      })
    }
  }

  case class AppliedTree[A](root: A, children: Seq[AppliedTree[A]])

  private val runner = new TestRunner


  val tests = Tests {
    'ignoreAttributes - {
      val dep = Dependency(
        mod"org:name",
        "0.1",
        configuration = Configuration("foo")
      )
      val deps = Seq(
        dep,
        dep.copy(attributes = Attributes(Type("fooz"), Classifier.empty))
      )

      val res = Print.dependenciesUnknownConfigs(deps, Map(), printExclusions = false, reorder = true)
      val expectedRes = "org:name:0.1:foo"

      assert(res == expectedRes)
    }

    'reverseTree - {
      val junit = mod"junit:junit"
      val junitVersion = "4.10"

      runner.resolve(Seq(Dependency(junit, junitVersion))).map(result => {
        val hamcrest = mod"org.hamcrest:hamcrest-core"
        val hamcrestVersion = "1.1"
        val t = ReverseModuleTree(
          result,
          Seq(hamcrest),
          withExclusions = true
        )
        val reverseTree = Tree(t.toVector)(_.dependees)

        val applied = AppliedTree.apply(reverseTree)
        assert(applied.length == 1)

        val expectedHead = applied.head
        assert(expectedHead.root.module == hamcrest)
        assert(expectedHead.root.reconciledVersion == hamcrestVersion)
        assert(expectedHead.children.length == 1)

        val expectedChild = expectedHead.children.head
        assert(expectedChild.root.module == junit)
        assert(expectedChild.root.reconciledVersion == junitVersion)
      })
    }
  }

}
