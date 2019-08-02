package coursier.util

import coursier.{moduleNameString, moduleString, organizationString}
import utest._

object ModuleMatchersTests extends TestSuite {

  val tests = Tests {

    * - {
      val matcher = ModuleMatchers(
        exclude = Set(ModuleMatcher(mod"io.circe:*")),
        include = Set(ModuleMatcher(mod"io.circe:circe-*")))
      val shouldMatch = Seq(
        mod"io.circe:circe-core_2.12",
        mod"io.circe:circe-generic_2.12",
        mod"io.circe:circe-foo_2.12"
      )
      val shouldNotMatch = Seq(
        mod"io.circe:foo-circe-core_2.12"
      )

      for (m <- shouldMatch)
        assert(matcher.matches(m))

      for (m <- shouldNotMatch)
        assert(!matcher.matches(m))
    }

    * - {
      val matcher = ModuleMatchers(
        exclude = Set(ModuleMatcher(mod"org.scala-lang:*")),
        include = Set())
      val shouldMatch = Seq(
        mod"io.circe:circe-core_2.12",
        mod"io.circe:circe-generic_2.12",
        mod"io.circe:circe-foo_2.12"
      )
      val shouldNotMatch = Seq(
        mod"org.scala-lang:scala-library"
      )

      for (m <- shouldMatch)
        assert(matcher.matches(m))

      for (m <- shouldNotMatch)
        assert(!matcher.matches(m))
    }
  }

}
