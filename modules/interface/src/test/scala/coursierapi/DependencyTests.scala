package coursierapi

import coursier.internal.api.ApiHelper
import utest._

object DependencyTests extends TestSuite {

  val tests = Tests {

    val initialDep = Dependency.of("org", "name", "1.2")

    test("exclusions") {
      val dep = Dependency.of(initialDep)
        .addExclusion("foo", "*")

      val dep0 = ApiHelper.dependency(ApiHelper.dependency(dep))

      assert(dep != initialDep)
      assert(dep == dep0)
    }

    test("configuration") {
      val dep = Dependency.of(initialDep)
        .withConfiguration("foo")

      val dep0 = ApiHelper.dependency(ApiHelper.dependency(dep))

      assert(dep != initialDep)
      assert(dep == dep0)
    }

    test("type") {
      val dep = Dependency.of(initialDep)
        .withType("foo")

      val dep0 = ApiHelper.dependency(ApiHelper.dependency(dep))

      assert(dep != initialDep)
      assert(dep == dep0)
    }

    test("classifier") {
      val dep = Dependency.of(initialDep)
        .withClassifier("foo")

      val dep0 = ApiHelper.dependency(ApiHelper.dependency(dep))

      assert(dep != initialDep)
      assert(dep == dep0)
    }

    test("transitive") {
      val dep = Dependency.of(initialDep)
        .withTransitive(false)

      val dep0 = ApiHelper.dependency(ApiHelper.dependency(dep))

      assert(dep != initialDep)
      assert(dep == dep0)
    }

    test("overrides") {
      val dep = Dependency.of(initialDep)
        .withTransitive(false)
        .addOverride("some-org", "some-name", "1.2")
        .addOverride(
          new DependencyManagement.Key("other-org", "other-name", "a-type", "a-classifier"),
          new DependencyManagement.Values("a-config", "1.4", false)
            .addExclusion("nope-org", "nope-name")
            .addExclusion("nope-org2", "nope-name2")
        )

      val dep0 = ApiHelper.dependency(ApiHelper.dependency(dep))

      assert(dep != initialDep)
      assert(dep == dep0)
    }

  }

}
