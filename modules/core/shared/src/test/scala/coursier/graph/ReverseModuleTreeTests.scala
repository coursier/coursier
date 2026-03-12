package coursier.graph

import coursier.core.{Module, ModuleName, Organization}
import coursier.util.Tree
import coursier.version.{Version, VersionConstraint}
import utest._

object ReverseModuleTreeTests extends TestSuite {

  private def makeModule(org: String, name: String): Module =
    Module(Organization(org), ModuleName(name), Map.empty)

  // Build a large shared allDependees map simulating a large dependency graph
  private def makeLargeDepMap(size: Int)
    : Map[Module, Seq[(Module, VersionConstraint, Boolean, Boolean)]] =
    (0 until size)
      .map { i =>
        val mod = makeModule("org.example", s"dep-$i")
        val dependees = Seq(
          (makeModule("org.example", s"parent-$i"), VersionConstraint(s"1.$i.0"), false, false)
        )
        mod -> dependees
      }
      .toMap

  private def makeLargeVersionMap(size: Int): Map[Module, (VersionConstraint, Version)] =
    (0 until size)
      .map { i =>
        makeModule("org.example", s"dep-$i") -> (VersionConstraint(s"1.$i.0"), Version(s"1.$i.0"))
      }
      .toMap

  val tests = Tests {

    test("nodeHashCodeIsCached") {
      val allDependees = makeLargeDepMap(500)
      val versions     = makeLargeVersionMap(500)

      val node = ReverseModuleTree.Node(
        module = makeModule("org.example", "test"),
        reconciledVersionConstraint = VersionConstraint("1.0.0"),
        retainedVersion0 = Version("1.0.0"),
        dependsOnModule = makeModule("org.example", "parent"),
        dependsOnVersionConstraint = VersionConstraint("1.0.0"),
        dependsOnRetainedVersion0 = Version("1.0.0"),
        excludedDependsOn = false,
        endorsedDependsOn = false,
        allDependees = allDependees,
        versions = versions,
        rootDependencies = Map.empty
      )

      // First call computes and caches
      val hash1 = node.hashCode
      // Second call should return cached value
      val hash2 = node.hashCode
      assert(hash1 == hash2)
    }

    test("nodeHashCodePerformance") {
      // Simulate a large dependency graph with 500 modules
      val allDependees = makeLargeDepMap(50000)
      val versions     = makeLargeVersionMap(50000)

      // Create many nodes sharing the same large maps (like in real resolution)
      val nodes = (0 until 10000).map { i =>
        ReverseModuleTree.Node(
          module = makeModule("org.example", s"node-$i"),
          reconciledVersionConstraint = VersionConstraint("1.0.0"),
          retainedVersion0 = Version("1.0.0"),
          dependsOnModule = makeModule("org.example", "parent"),
          dependsOnVersionConstraint = VersionConstraint("1.0.0"),
          dependsOnRetainedVersion0 = Version("1.0.0"),
          excludedDependsOn = false,
          endorsedDependsOn = false,
          allDependees = allDependees,
          versions = versions,
          rootDependencies = Map.empty
        )
      }

      // Simulate what Tree.recursivePrint does: Set.contains and Set.+
      // With uncached hashCode on case class, this would hash 500-entry maps
      // on every contains/add call, taking O(n * mapSize) time.
      val start = System.nanoTime()
      var set   = Set.empty[ReverseModuleTree.Node]
      for (node <- nodes) {
        set.contains(node)
        set = set + node
      }
      val elapsedMs = (System.nanoTime() - start) / 1000000

      // With cached hashCode, this should complete in well under 1 second.
      // Without caching, 100 nodes * 500-entry maps would take much longer.
      assert(elapsedMs < 1000L)
    }
  }
}
