package coursier.core

import utest._

import coursier.core.VariantSelector.VariantMatcher

object VariantMatcherTests extends TestSuite {

  // attributes of the runtime variant of org.springframework:spring-core:6.1.13,
  // which requires Java 17
  private val springCore6RuntimeAttributes = Map(
    "org.gradle.category"            -> "library",
    "org.gradle.dependency.bundling" -> "external",
    "org.gradle.jvm.version"         -> "17",
    "org.gradle.libraryelements"     -> "jar",
    "org.gradle.usage"               -> "java-runtime"
  )

  private def runtimeSelector(jvmVersion: String): VariantSelector.AttributesBased =
    VariantSelector.AttributesBased(
      Map(
        "org.gradle.category" -> VariantMatcher.Library,
        "org.gradle.usage"    -> VariantMatcher.Runtime,
        VariantMatcher.fromString("org.gradle.jvm.version", jvmVersion)
      )
    )

  val tests = Tests {

    test("jvmVersion") {
      test("matcher") {
        val (key, matcher) = VariantMatcher.fromString("org.gradle.jvm.version", "17")
        assert(key == "org.gradle.jvm.version")
        assert(matcher.matches("8").nonEmpty)
        assert(matcher.matches("11").nonEmpty)
        assert(matcher.matches("17").nonEmpty)
        assert(matcher.matches("21").isEmpty)
      }
      test("exact") {
        val (_, matcher) = VariantMatcher.fromString("org.gradle.jvm.version!", "17")
        assert(matcher.matches("11").isEmpty)
        assert(matcher.matches("17").nonEmpty)
        assert(matcher.matches("21").isEmpty)
      }
      test("olderJvmRejectsNewerVariant") {
        assert(runtimeSelector("11").matches(springCore6RuntimeAttributes).isEmpty)
      }
      test("sameJvmAcceptsVariant") {
        assert(runtimeSelector("17").matches(springCore6RuntimeAttributes).nonEmpty)
      }
      test("newerJvmAcceptsOlderVariant") {
        assert(runtimeSelector("21").matches(springCore6RuntimeAttributes).nonEmpty)
      }
    }
  }
}
