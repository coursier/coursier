package coursier.tests.util

import coursier.core.{
  Attributes,
  Classifier,
  Configuration,
  Dependency,
  Reconciliation,
  Type,
  VariantSelector
}
import coursier.tests.TestRunner
import coursier.graph.{DependencyTree, ReverseModuleTree}
import coursier.util.{Print, Tree}
import coursier.util.StringInterpolators._
import coursier.version.{ConstraintReconciliation, VersionConstraint}
import utest._

import scala.async.Async.{async, await}
import scala.concurrent.ExecutionContext.Implicits.global

object PrintTests extends TestSuite {

  object AppliedTree {
    def apply[A](tree: Tree[A]): Seq[AppliedTree[A]] =
      tree.roots.map { root =>
        AppliedTree[A](root, apply(Tree(tree.children(root).toIndexedSeq)(tree.children)))
      }
  }

  case class AppliedTree[A](root: A, children: Seq[AppliedTree[A]])

  private val runner = new TestRunner

  val tests = Tests {
    test("ignoreAttributes") {
      val dep = dep"org:name:0.1"
        .withVariantSelector(VariantSelector.ConfigurationBased(Configuration("foo")))
      val deps = Seq(
        dep,
        dep.withAttributes(Attributes(Type("fooz"), Classifier.empty))
      )

      val res =
        Print.dependenciesUnknownConfigs0(deps, Map(), printExclusions = false, reorder = true)
      val expectedRes = "org:name:0.1:foo"

      assert(res == expectedRes)
    }

    test("reverseTree") {
      test - async {
        val junit        = mod"junit:junit"
        val junitVersion = VersionConstraint("4.10")

        val result = await(runner.resolve(Seq(Dependency(junit, junitVersion))))

        val hamcrest        = mod"org.hamcrest:hamcrest-core"
        val hamcrestVersion = VersionConstraint("1.1")
        val t               = ReverseModuleTree(
          result,
          Seq(hamcrest),
          withExclusions = true
        )
        val reverseTree = Tree(t.toVector)(_.dependees)

        val applied = AppliedTree.apply(reverseTree)
        assert(applied.length == 1)

        val expectedHead = applied.head
        assert(expectedHead.root.module == hamcrest)
        assert(expectedHead.root.reconciledVersionConstraint == hamcrestVersion)
        assert(expectedHead.children.length == 1)

        val expectedChild = expectedHead.children.head
        assert(expectedChild.root.module == junit)
        assert(expectedChild.root.reconciledVersionConstraint == junitVersion)
      }

      test - async {
        val mod     = mod"org.webjars.npm:micromatch"
        val version = VersionConstraint("2.3.11")

        val result = await(runner.resolve(
          Seq(Dependency(mod, version)),
          forceVersions =
            Map(mod"org.webjars.npm:caniuse-lite" -> VersionConstraint("1.0.30000748")),
          reconciliation = Some(_ => ConstraintReconciliation.Relaxed)
        ))

        val t = DependencyTree(result)
        assert(t.length == 1)

        val root = t.head
        assert(root.dependency.module == mod)

        val rendered = Tree(t.toVector)(_.children)
          .render(d => s"${d.dependency.module.repr}:${d.retainedVersion0.asString}")

        val expected =
          """└─ org.webjars.npm:micromatch:2.3.11
            |   ├─ org.webjars.npm:arr-diff:2.0.0
            |   │  └─ org.webjars.npm:arr-flatten:1.1.0
            |   ├─ org.webjars.npm:array-unique:0.2.1
            |   ├─ org.webjars.npm:braces:1.8.5
            |   │  ├─ org.webjars.npm:expand-range:1.8.2
            |   │  │  └─ org.webjars.npm:fill-range:2.2.4
            |   │  │     ├─ org.webjars.npm:is-number:4.0.0
            |   │  │     ├─ org.webjars.npm:isobject:2.1.0
            |   │  │     │  └─ org.webjars.npm:isarray:1.0.0
            |   │  │     ├─ org.webjars.npm:randomatic:3.1.1
            |   │  │     │  ├─ org.webjars.npm:is-number:4.0.0
            |   │  │     │  ├─ org.webjars.npm:kind-of:6.0.3
            |   │  │     │  └─ org.webjars.npm:math-random:1.0.4
            |   │  │     ├─ org.webjars.npm:repeat-element:1.1.4
            |   │  │     └─ org.webjars.npm:repeat-string:1.6.1
            |   │  ├─ org.webjars.npm:preserve:0.2.0
            |   │  └─ org.webjars.npm:repeat-element:1.1.4
            |   ├─ org.webjars.npm:expand-brackets:0.1.5
            |   │  └─ org.webjars.npm:is-posix-bracket:0.1.1
            |   ├─ org.webjars.npm:extglob:0.3.2
            |   │  └─ org.webjars.npm:is-extglob:1.0.0
            |   ├─ org.webjars.npm:filename-regex:2.0.1
            |   ├─ org.webjars.npm:is-extglob:1.0.0
            |   ├─ org.webjars.npm:is-glob:2.0.1
            |   │  └─ org.webjars.npm:is-extglob:1.0.0
            |   ├─ org.webjars.npm:kind-of:6.0.3
            |   ├─ org.webjars.npm:normalize-path:2.1.1
            |   │  └─ org.webjars.npm:remove-trailing-separator:1.1.0
            |   ├─ org.webjars.npm:object.omit:2.0.1-1
            |   │  ├─ org.webjars.npm:for-own:0.1.5-1
            |   │  │  └─ org.webjars.npm:for-in:1.0.2
            |   │  └─ org.webjars.npm:is-extendable:0.1.1
            |   ├─ org.webjars.npm:parse-glob:3.0.4
            |   │  ├─ org.webjars.npm:glob-base:0.3.0
            |   │  │  ├─ org.webjars.npm:glob-parent:2.0.0
            |   │  │  │  └─ org.webjars.npm:is-glob:2.0.1
            |   │  │  │     └─ org.webjars.npm:is-extglob:1.0.0
            |   │  │  └─ org.webjars.npm:is-glob:2.0.1
            |   │  │     └─ org.webjars.npm:is-extglob:1.0.0
            |   │  ├─ org.webjars.npm:is-dotfile:1.0.3
            |   │  ├─ org.webjars.npm:is-extglob:1.0.0
            |   │  └─ org.webjars.npm:is-glob:2.0.1
            |   │     └─ org.webjars.npm:is-extglob:1.0.0
            |   └─ org.webjars.npm:regex-cache:0.4.4
            |      └─ org.webjars.npm:is-equal-shallow:0.1.3
            |         └─ org.webjars.npm:is-primitive:2.0.0""".stripMargin

        assert(rendered.replace("\r\n", "\n") == expected)
      }
    }
  }

}
