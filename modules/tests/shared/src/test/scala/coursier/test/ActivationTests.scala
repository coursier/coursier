package coursier.test

import coursier.core.{Activation, Parse}
import coursier.core.Activation.Os
import utest._

object ActivationTests extends TestSuite {

  def parseVersion(s: String) = Parse.version(s).getOrElse(???)
  def parseVersionInterval(s: String) = Parse.versionInterval(s).getOrElse(???)

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
    test("OS") {
      test("fromProperties") {
        test("MacOSX") {
          val props = Map(
            "os.arch" -> "x86_64",
            "os.name" -> "Mac OS X",
            "os.version" -> "10.12",
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

        test("linuxPi") {
          val props = Map(
            "os.arch" -> "arm",
            "os.name" -> "Linux",
            "os.version" -> "4.1.13-v7+",
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

      test("active") {

        test("arch") {
          val activation = Os(Some("x86_64"), Set(), None, None)

          val isActive = activation.isActive(macOs)

          assert(isActive)
        }

        test("wrongArch") {
          val activation = Os(Some("arm"), Set(), None, None)

          val isActive = activation.isActive(macOs)

          assert(!isActive)
        }

        test("family") {
          val activation = Os(None, Set("mac"), None, None)

          val isActive = activation.isActive(macOs)

          assert(isActive)
        }

        test("wrongFamily") {
          val activation = Os(None, Set("windows"), None, None)

          val isActive = activation.isActive(macOs)

          assert(!isActive)
        }

        test("name") {
          val activation = Os(None, Set(), Some("mac os x"), None)

          val isActive = activation.isActive(macOs)

          assert(isActive)
        }

        test("wrongName") {
          val activation = Os(None, Set(), Some("linux"), None)

          val isActive = activation.isActive(macOs)

          assert(!isActive)
        }

        test("version") {
          val activation = Os(None, Set(), None, Some("10.12"))

          val isActive = activation.isActive(macOs)

          assert(isActive)
        }

        test("wrongVersion") {
          val activation = Os(None, Set(), None, Some("10.11"))

          val isActive = activation.isActive(macOs)

          assert(!isActive)
        }
      }
    }

    test("properties") {
      val activation = Activation.empty.withProperties(
        Seq(
          "required" -> None,
          "requiredWithValue" -> Some("foo"),
          "requiredWithNegValue" -> Some("!bar")
        )
      )

      test("match") {
        val isActive = activation.isActive(
          Map(
            "required" -> "a",
            "requiredWithValue" -> "foo",
            "requiredWithNegValue" -> "baz"
          ),
          Os.empty,
          None
        )

        assert(isActive)
      }

      test("noMatch") {
        * - {
          val isActive = activation.isActive(
            Map(
              "requiredWithValue" -> "foo",
              "requiredWithNegValue" -> "baz"
            ),
            Os.empty,
            None
          )

          assert(!isActive)
        }

        * - {
          val isActive = activation.isActive(
            Map(
              "required" -> "a",
              "requiredWithValue" -> "fooz",
              "requiredWithNegValue" -> "baz"
            ),
            Os.empty,
            None
          )

          assert(!isActive)
        }

        * - {
          val isActive = activation.isActive(
            Map(
              "required" -> "a",
              "requiredWithValue" -> "foo",
              "requiredWithNegValue" -> "bar"
            ),
            Os.empty,
            None
          )

          assert(!isActive)
        }
      }
    }

    test("jdkVersion") {

      test("match") {
        test("exactVersion") {
          val activation = Activation(
            Nil,
            Os.empty,
            Some(Right(Seq(parseVersion("1.8.0_112"))))
          )

          val isActive = activation.isActive(Map(), Os.empty, Some(jdkVersion))

          assert(isActive)
        }

        test("exactVersionSeveral") {
          val activation = Activation(
            Nil,
            Os.empty,
            Some(Right(Seq(parseVersion("1.8.0_102"), parseVersion("1.8.0_112"))))
          )

          val isActive = activation.isActive(Map(), Os.empty, Some(jdkVersion))

          assert(isActive)
        }


        test("wrongExactVersion") {
          val activation = Activation(
            Nil,
            Os.empty,
            Some(Right(Seq(parseVersion("1.8.0_102"))))
          )

          val isActive = activation.isActive(Map(), Os.empty, Some(jdkVersion))

          assert(!isActive)
        }


        test("wrongExactVersionSeveral") {
          val activation = Activation(
            Nil,
            Os.empty,
            Some(Right(Seq(parseVersion("1.8.0_92"), parseVersion("1.8.0_102"))))
          )

          val isActive = activation.isActive(Map(), Os.empty, Some(jdkVersion))

          assert(!isActive)
        }

        test("versionInterval") {
          val activation = Activation(
            Nil,
            Os.empty,
            Some(Left(parseVersionInterval("[1.8,)")))
          )

          val isActive = activation.isActive(Map(), Os.empty, Some(jdkVersion))

          assert(isActive)
        }

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

    test("all") {
      val activation = Activation(
        Seq(
          "required" -> None,
          "requiredWithValue" -> Some("foo"),
          "requiredWithNegValue" -> Some("!bar")
        ),
        Os(None, Set("mac"), None, None),
        Some(Left(parseVersionInterval("[1.8,)")))
      )

      test("match") {
        val isActive = activation.isActive(
          Map(
            "required" -> "a",
            "requiredWithValue" -> "foo",
            "requiredWithNegValue" -> "baz"
          ),
          macOs,
          Some(jdkVersion)
        )

        assert(isActive)
      }

      test("noMatch") {
        val isActive = activation.isActive(
          Map(
            "requiredWithValue" -> "foo",
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
