package coursierapi

import coursierapi.Interpolators._
import utest._

object InterpolatorTests extends TestSuite {

  val tests = Tests {
    test("module") {
      test {
        val mod = mod"some.org:a-module"
        val expectedMod = Module.of("some.org", "a-module")
        assert(mod == expectedMod)
      }
    }

    test("dependency") {
      test {
        val dep = dep"some.org:a-module:0.1.2"
        val expectedDep = Dependency.of("some.org", "a-module", "0.1.2")
        assert(dep == expectedDep)
      }
    }

    test("repository") {
      test("maven") {
        test {
          val repo = mvn"https://artifacts.com.pany/maven"
          val expectedRepo = MavenRepository.of("https://artifacts.com.pany/maven")
          assert(repo == expectedRepo)
        }
      }

      test("ivy") {
        test {
          val repo = ivy"https://foo.com/a/b/c/[defaultPattern]"
          val expectedRepo = IvyRepository.of("https://foo.com/a/b/c/[defaultPattern]")
          assert(repo == expectedRepo)
        }
      }
    }
  }

}
