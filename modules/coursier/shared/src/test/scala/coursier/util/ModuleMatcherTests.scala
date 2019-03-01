package coursier.util

import coursier.{moduleNameString, moduleString, organizationString}
import utest._

object ModuleMatcherTests extends TestSuite {

  val tests = Tests {

    * - {
      val matcher = ModuleMatcher(org"io.circe", name"circe-*")
      val shouldMatch = Seq(
        mod"io.circe:circe-core_2.12",
        mod"io.circe:circe-generic_2.12",
        mod"io.circe:circe-foo_2.12"
      )
      val shouldNotMatch = Seq(
        mod"io.circe:circo-core_2.12",
        mod"io.circe:foo-circe-core_2.12",
        mod"io.circo:circe-core_2.12",
        mod"ioocirce:circe-foo_2.12"
      )

      for (m <- shouldMatch)
        assert(matcher.matches(m))

      for (m <- shouldNotMatch)
        assert(!matcher.matches(m))
    }

    * - {
      val matcher = ModuleMatcher(org"org.*", name"scala-library")
      val shouldMatch = Seq(
        mod"org.scala-lang:scala-library",
        mod"org.typelevel:scala-library"
      )
      val shouldNotMatch = Seq(
        mod"org.scala-lang:scala-compiler",
        mod"org.typelevel:scala-reflect"
      )

      for (m <- shouldMatch)
        assert(matcher.matches(m))

      for (m <- shouldNotMatch)
        assert(!matcher.matches(m))
    }

    * - {
      val matcher = ModuleMatcher(org"io.foo", name"foo-*_2.12")
      val shouldMatch = Seq(
        mod"io.foo:foo-core_2.12",
        mod"io.foo:foo-data_2.12"
      )
      val shouldNotMatch = Seq(
        mod"io.foo:foo-core_2.11",
        mod"io.foo:foo-core",
        mod"io.fooo:foo-core_2.12",
        mod"io.foo:foo-data_2o12"
      )

      for (m <- shouldMatch)
        assert(matcher.matches(m))

      for (m <- shouldNotMatch)
        assert(!matcher.matches(m))
    }

    'all - {
      val matcher = ModuleMatcher(org"*", name"*")
      val shouldMatch = Seq(
        mod"io.foo:foo-core_2.12",
        mod"io.foo:foo-data_2.12",
        mod"io.foo:foo-core_2.11",
        mod"io.foo:foo-core",
        mod"io.fooo:foo-core_2.12",
        mod":",
        mod"io.fooo:",
        mod":foo-core_2.12"
      )

      for (m <- shouldMatch)
        assert(matcher.matches(m))
    }

  }

}
