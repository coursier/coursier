package coursier.util

import coursier.core.Configuration
import coursier.ivy.{IvyRepository, Pattern}
import coursier.maven.MavenRepository
import coursier.{Dependency, Module, dependencyString, ivyRepositoryString, mavenRepositoryString, moduleNameString, moduleString, organizationString}
import utest._

object InterpolatorsTests extends TestSuite {

  val tests = Tests {
    test("module") {
      test {
        val m = mod"org.scala-lang:scala-library"
        val expected = Module(org"org.scala-lang", name"scala-library", Map())
        assert(m == expected)
      }

      test {
        val m = mod"org.scala-lang:scala-library;foo=a;b=c"
        val expected = Module(org"org.scala-lang", name"scala-library", Map("foo" -> "a", "b" -> "c"))
        assert(m == expected)
      }
    }

    test("dependency") {
      test {
        val dep = dep"ch.qos.logback:logback-classic:1.1.3"
        val expected = Dependency(Module(org"ch.qos.logback", name"logback-classic"), "1.1.3")
        assert(dep == expected)
      }
      test {
        val dep = dep"org.scalatest:scalatest_2.12:3.0.1:test"
        val expected = Dependency(Module(org"org.scalatest", name"scalatest_2.12"), "3.0.1")
          .withConfiguration(Configuration.test)
        assert(dep == expected)
      }
    }

    "maven repository" - {
      test {
        val repo = mvn"https://foo.com/a/b/c"
        val expectedRepo = MavenRepository("https://foo.com/a/b/c")
        assert(repo == expectedRepo)
      }
    }

    "ivy repository" - {
      test {
        val repo = ivy"https://foo.com/a/b/c/[defaultPattern]"
        val expectedRepo = IvyRepository.parse("https://foo.com/a/b/c/[defaultPattern]").toOption.get
        assert(repo == expectedRepo)
        assert(repo.pattern.chunks.endsWith(Pattern.default.chunks))
      }
    }

    // shapeless.test.illTyped could help test malformed string literals
  }

}
