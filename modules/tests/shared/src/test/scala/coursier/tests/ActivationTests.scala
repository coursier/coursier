package coursier.tests

import coursier.core.Activation
import coursier.core.Activation.Os
import coursier.version.VersionParse
import utest._

object ActivationTests extends TestSuite {

  def parseVersion(s: String)         = VersionParse.version(s).getOrElse(???)
  def parseVersionInterval(s: String) = VersionParse.versionInterval(s).getOrElse(???)

  val macOs = Os(
    Some("x86_64"),
    Set("mac", "unix"),
    Some("mac os x"),
    Some("10.12")
  )

  val jdkVersion = parseVersion("1.8.0_112")

  // missing:
  // - condition on OS or JDK, but no OS or JDK info provided (-> no match)
  // - negated OS infos (starting with "!") - not implemented yet

  val tests = Tests {
    /** Verifies the `OS` scenario behaves as the user expects. */
    test("OS") {
      /** Verifies the `fromProperties` scenario behaves as the user expects. */
      test("fromProperties") {
        /** Verifies the `MacOSX` scenario behaves as the user expects. */
        test("MacOSX") {
          val props = Map(
            "os.arch"        -> "x86_64",
            "os.name"        -> "Mac OS X",
            "os.version"     -> "10.12",
            "path.separator" -> ":"
          )

          val expectedOs = Os(
            Some("x86_64"),
            Set("mac", "unix"),
            Some("mac os x"),
            Some("10.12")
          )

          val os = Os.fromProperties(props)

          assert(os == expectedOs)
        }

        /** Verifies the `linuxPi` scenario behaves as the user expects. */
        test("linuxPi") {
          val props = Map(
            "os.arch"        -> "arm",
            "os.name"        -> "Linux",
            "os.version"     -> "4.1.13-v7+",
            "path.separator" -> ":"
          )

          val expectedOs = Os(
            Some("arm"),
            Set("unix"),
            Some("linux"),
            Some("4.1.13-v7+")
          )

          val os = Os.fromProperties(props)

          assert(os == expectedOs)
        }
      }

      /** Verifies the `active` scenario behaves as the user expects. */
      test("active") {

        /** Verifies the `arch` scenario behaves as the user expects. */
        test("arch") {
          val activation = Os(Some("x86_64"), Set(), None, None)

          val isActive = activation.isActive(macOs)

          assert(isActive)
        }

        /** Verifies the `wrongArch` scenario behaves as the user expects. */
        test("wrongArch") {
          val activation = Os(Some("arm"), Set(), None, None)

          val isActive = activation.isActive(macOs)

          assert(!isActive)
        }

        /** Verifies the `family` scenario behaves as the user expects. */
        test("family") {
          val activation = Os(None, Set("mac"), None, None)

          val isActive = activation.isActive(macOs)

          assert(isActive)
        }

        /** Verifies the `wrongFamily` scenario behaves as the user expects. */
        test("wrongFamily") {
          val activation = Os(None, Set("windows"), None, None)

          val isActive = activation.isActive(macOs)

          assert(!isActive)
        }

        /** Verifies the `name` scenario behaves as the user expects. */
        test("name") {
          val activation = Os(None, Set(), Some("mac os x"), None)

          val isActive = activation.isActive(macOs)

          assert(isActive)
        }

        /** Verifies the `wrongName` scenario behaves as the user expects. */
        test("wrongName") {
          val activation = Os(None, Set(), Some("linux"), None)

          val isActive = activation.isActive(macOs)

          assert(!isActive)
        }

        /** Verifies the `version` scenario behaves as the user expects. */
        test("version") {
          val activation = Os(None, Set(), None, Some("10.12"))

          val isActive = activation.isActive(macOs)

          assert(isActive)
        }

        /** Verifies the `wrongVersion` scenario behaves as the user expects. */
        test("wrongVersion") {
          val activation = Os(None, Set(), None, Some("10.11"))

          val isActive = activation.isActive(macOs)

          assert(!isActive)
        }
      }
    }

    /** Verifies the `properties` scenario behaves as the user expects. */
    test("properties") {
      val activation = Activation.empty.withProperties(
        Seq(
          "required"             -> None,
          "requiredWithValue"    -> Some("foo"),
          "requiredWithNegValue" -> Some("!bar")
        )
      )

      /** Verifies the `match` scenario behaves as the user expects. */
      test("match") {
        val isActive = activation.isActive(
          Map(
            "required"             -> "a",
            "requiredWithValue"    -> "foo",
            "requiredWithNegValue" -> "baz"
          ),
          Os.empty,
          None
        )

        assert(isActive)
      }

      /** Verifies the `match with missing property` scenario behaves as the user expects. */
      test("match with missing property") {
        val isActive = activation.isActive(
          Map(
            "required"          -> "a",
            "requiredWithValue" -> "foo"
          ),
          Os.empty,
          None
        )

        assert(isActive)
      }

      /** Verifies the `noMatch` scenario behaves as the user expects. */
      test("noMatch") {
        test {
          val isActive = activation.isActive(
            Map(
              "requiredWithValue"    -> "foo",
              "requiredWithNegValue" -> "baz"
            ),
            Os.empty,
            None
          )

          assert(!isActive)
        }

        test {
          val isActive = activation.isActive(
            Map(
              "required"             -> "a",
              "requiredWithValue"    -> "fooz",
              "requiredWithNegValue" -> "baz"
            ),
            Os.empty,
            None
          )

          assert(!isActive)
        }

        test {
          val isActive = activation.isActive(
            Map(
              "required"             -> "a",
              "requiredWithValue"    -> "foo",
              "requiredWithNegValue" -> "bar"
            ),
            Os.empty,
            None
          )

          assert(!isActive)
        }
      }
    }

    /** Verifies the `jdkVersion` scenario behaves as the user expects. */
    test("jdkVersion") {

      /** Verifies the `match` scenario behaves as the user expects. */
      test("match") {
        /** Verifies the `exactVersion` scenario behaves as the user expects. */
        test("exactVersion") {
          val activation = Activation(
            Nil,
            Os.empty,
            Some(Right(Seq(parseVersion("1.8.0_112"))))
          )

          val isActive = activation.isActive(Map(), Os.empty, Some(jdkVersion))

          assert(isActive)
        }

        /** Verifies the `exactVersionSeveral` scenario behaves as the user expects. */
        test("exactVersionSeveral") {
          val activation = Activation(
            Nil,
            Os.empty,
            Some(Right(Seq(parseVersion("1.8.0_102"), parseVersion("1.8.0_112"))))
          )

          val isActive = activation.isActive(Map(), Os.empty, Some(jdkVersion))

          assert(isActive)
        }

        /** Verifies the `wrongExactVersion` scenario behaves as the user expects. */
        test("wrongExactVersion") {
          val activation = Activation(
            Nil,
            Os.empty,
            Some(Right(Seq(parseVersion("1.8.0_102"))))
          )

          val isActive = activation.isActive(Map(), Os.empty, Some(jdkVersion))

          assert(!isActive)
        }

        /** Verifies the `wrongExactVersionSeveral` scenario behaves as the user expects. */
        test("wrongExactVersionSeveral") {
          val activation = Activation(
            Nil,
            Os.empty,
            Some(Right(Seq(parseVersion("1.8.0_92"), parseVersion("1.8.0_102"))))
          )

          val isActive = activation.isActive(Map(), Os.empty, Some(jdkVersion))

          assert(!isActive)
        }

        /** Verifies the `versionInterval` scenario behaves as the user expects. */
        test("versionInterval") {
          val activation = Activation(
            Nil,
            Os.empty,
            Some(Left(parseVersionInterval("[1.8,)")))
          )

          val isActive = activation.isActive(Map(), Os.empty, Some(jdkVersion))

          assert(isActive)
        }

        /** Verifies the `wrongVersionInterval` scenario behaves as the user expects. */
        test("wrongVersionInterval") {
          val activation = Activation(
            Nil,
            Os.empty,
            Some(Left(parseVersionInterval("[1.7,1.8)")))
          )

          val isActive = activation.isActive(Map(), Os.empty, Some(jdkVersion))

          assert(!isActive)
        }
      }
    }

    /** Verifies the `all` scenario behaves as the user expects. */
    test("all") {
      val activation = Activation(
        Seq(
          "required"             -> None,
          "requiredWithValue"    -> Some("foo"),
          "requiredWithNegValue" -> Some("!bar")
        ),
        Os(None, Set("mac"), None, None),
        Some(Left(parseVersionInterval("[1.8,)")))
      )

      /** Verifies the `match` scenario behaves as the user expects. */
      test("match") {
        val isActive = activation.isActive(
          Map(
            "required"             -> "a",
            "requiredWithValue"    -> "foo",
            "requiredWithNegValue" -> "baz"
          ),
          macOs,
          Some(jdkVersion)
        )

        assert(isActive)
      }

      /** Verifies the `noMatch` scenario behaves as the user expects. */
      test("noMatch") {
        val isActive = activation.isActive(
          Map(
            "requiredWithValue"    -> "foo",
            "requiredWithNegValue" -> "baz"
          ),
          macOs,
          Some(jdkVersion)
        )

        assert(!isActive)
      }
    }
  }

}
