package coursier.complete

import coursier.Repositories
import utest._

import scala.async.Async.{async, await}

object CompleteTests extends TestSuite {

  import coursier.TestHelpers.{ec, cache}

  val tests = Tests {

    val complete = Complete(cache)
      .withRepositories(Seq(
        Repositories.central
      ))
      .withScalaVersion("2.12.8")

    def simple(input: String, expected: (Int, Seq[String])) =
      async {
        val res = await {
          complete
            .withInput(input)
            .complete()
            .future()
        }

        assert(res == expected)
      }

    * - simple("io.get-c", (0, Seq("io.get-coursier")))
    * - simple("io.get-coursier", (0, Seq("io.get-coursier")))
    * - simple("io.get-coursierz", (0, Nil))

    * - {
      val expected = 16 -> Seq(
        "coursier-bootstrap_2.11", 
        "coursier-bootstrap_2.12", 
        "coursier-bootstrap_2.13.0-M5", 
        "coursier-cache-java-6_2.10", 
        "coursier-cache-java-6_2.11", 
        "coursier-cache_2.10", 
        "coursier-cache_2.11",
        "coursier-cache_2.12",
        "coursier-cache_2.12.0-RC2",
        "coursier-cache_2.13.0-M5",
        "coursier-cats-interop_2.11",
        "coursier-cats-interop_2.12",
        "coursier-cats-interop_2.13.0-M5",
        "coursier-cats-interop_sjs0.6_2.11",
        "coursier-cats-interop_sjs0.6_2.12", 
        "coursier-cats-interop_sjs0.6_2.13.0-M5",
        "coursier-cli-java-6_2.11",
        "coursier-cli_2.11",
        "coursier-cli_2.12", 
        "coursier-core_2.11", 
        "coursier-core_2.12", 
        "coursier-core_2.13.0-M5",
        "coursier-core_sjs0.6_2.11", 
        "coursier-core_sjs0.6_2.12",
        "coursier-core_sjs0.6_2.13.0-M5",
        "coursier-extra_2.10", 
        "coursier-extra_2.11", 
        "coursier-extra_2.12",
        "coursier-extra_2.13.0-M5",
        "coursier-fetch-js_sjs0.6_2.10", 
        "coursier-fetch-js_sjs0.6_2.11", 
        "coursier-fetch-js_sjs0.6_2.12", 
        "coursier-fetch-js_sjs0.6_2.13.0-M5", 
        "coursier-java-6_2.10", 
        "coursier-java-6_2.11", 
        "coursier-java-6_sjs0.6_2.10", 
        "coursier-java-6_sjs0.6_2.11", 
        "coursier-okhttp_2.10", 
        "coursier-okhttp_2.11", 
        "coursier-okhttp_2.12", 
        "coursier-okhttp_2.12.0-RC2", 
        "coursier-okhttp_2.13.0-M5", 
        "coursier-sbt-launcher_2.12", 
        "coursier-scalaz-interop_2.10", 
        "coursier-scalaz-interop_2.11", 
        "coursier-scalaz-interop_2.12", 
        "coursier-scalaz-interop_2.13.0-M5", 
        "coursier-scalaz-interop_sjs0.6_2.10", 
        "coursier-scalaz-interop_sjs0.6_2.11", 
        "coursier-scalaz-interop_sjs0.6_2.12", 
        "coursier-scalaz-interop_sjs0.6_2.13.0-M5", 
        "coursier_2.10", 
        "coursier_2.11", 
        "coursier_2.12", 
        "coursier_2.12.0-RC2", 
        "coursier_2.13.0-M5", 
        "coursier_sjs0.6_2.10", 
        "coursier_sjs0.6_2.11", 
        "coursier_sjs0.6_2.12", 
        "coursier_sjs0.6_2.12.0-RC2", 
        "coursier_sjs0.6_2.13.0-M5", 
        "echo", 
        "echo_2.10", 
        "echo_2.11", 
        "echo_2.12", 
        "echo_native0.3_2.11", 
        "http-server-java7_2.10", 
        "http-server-java7_2.11", 
        "http-server_2.10", 
        "http-server_2.11", 
        "http-server_2.12", 
        "interface", 
        "interface_2.11", 
        "interface_2.12", 
        "interpolators_2.11", 
        "interpolators_2.12", 
        "jarjar", 
        "lm-coursier-shaded_2.12", 
        "lm-coursier_2.12", 
        "props", 
        "rules-parser_2.11", 
        "rules-parser_2.12", 
        "rules-parser_2.13.0-M5", 
        "rules-parser_sjs0.6_2.11", 
        "rules-parser_sjs0.6_2.12", 
        "rules-parser_sjs0.6_2.13.0-M5", 
        "sbt-coursier-java-6_2.10_0.13", 
        "sbt-coursier-shared-shaded_2.12_1.0", 
        "sbt-coursier-shared_2.12_1.0", 
        "sbt-coursier_2.10_0.13", 
        "sbt-coursier_2.12_1.0", 
        "sbt-coursier_2.12_1.0.0-M5", 
        "sbt-coursier_2.12_1.0.0-M6", 
        "sbt-launcher-plugin_2.10_0.13", 
        "sbt-launcher-plugin_2.12_1.0", 
        "sbt-launcher-scripted-plugin_2.12_1.0", 
        "sbt-launcher_2.10", 
        "sbt-launcher_2.11", 
        "sbt-launcher_2.12", 
        "sbt-lm-coursier_2.12_1.0", 
        "sbt-pgp-coursier_2.10_0.13", 
        "sbt-pgp-coursier_2.12_1.0", 
        "sbt-pgp-coursier_2.12_1.0.0-M6", 
        "sbt-shading_2.10_0.13", 
        "sbt-shading_2.12_1.0", 
        "sbt-shading_2.12_1.0.0-M5", 
        "sbt-shading_2.12_1.0.0-M6", 
        "sbt-shared_2.10_0.13", 
        "sbt-shared_2.12_1.0", 
        "scala-native", 
        "simple-web-server_2.10", 
        "simple-web-server_2.11", 
        "web_sjs0.6_2.12"
      )

      simple("io.get-coursier:", expected)
    }

    * - {
      val expected = 17 -> Seq(
        "coursier-bootstrap",
        "coursier-cache", 
        "coursier-cats-interop", 
        "coursier-cats-interop_sjs0.6", 
        "coursier-cli", 
        "coursier-core", 
        "coursier-core_sjs0.6", 
        "coursier-extra", 
        "coursier-fetch-js_sjs0.6", 
        "coursier-okhttp", 
        "coursier-sbt-launcher", 
        "coursier-scalaz-interop", 
        "coursier-scalaz-interop_sjs0.6", 
        "coursier", 
        "coursier_sjs0.6", 
        "echo", 
        "http-server", 
        "interface", 
        "interpolators", 
        "lm-coursier-shaded", 
        "lm-coursier", 
        "rules-parser", 
        "rules-parser_sjs0.6", 
        "sbt-launcher", 
        "web_sjs0.6"
      )
      simple("io.get-coursier::", expected)
    }

    * - {
      val expected = 17 -> Seq(
        "coursier-bootstrap",
        "coursier-cache",
        "coursier-cats-interop",
        "coursier-cats-interop_sjs0.6",
        "coursier-cli",
        "coursier-core",
        "coursier-core_sjs0.6",
        "coursier-extra",
        "coursier-fetch-js_sjs0.6",
        "coursier-okhttp",
        "coursier-sbt-launcher",
        "coursier-scalaz-interop",
        "coursier-scalaz-interop_sjs0.6",
        "coursier",
        "coursier_sjs0.6"
      )
      simple("io.get-coursier::cour", expected)
    }

    * - simple("io.get-coursier::coursier-cache", 17 -> Seq("coursier-cache"))

    * - {
      val expected = 16 -> Seq(
        "coursier-cache-java-6_2.10",
        "coursier-cache-java-6_2.11", 
        "coursier-cache_2.10", 
        "coursier-cache_2.11", 
        "coursier-cache_2.12", 
        "coursier-cache_2.12.0-RC2", 
        "coursier-cache_2.13.0-M5"
      )
      simple("io.get-coursier:coursier-cache", expected)
    }

    * - {
      val expected = 32 -> Seq(
        "1.0.0-M14-7",
        "1.0.0-M14-8", 
        "1.0.0-M14-9", 
        "1.0.0-M15", 
        "1.0.0-M15-1", 
        "1.0.0-M15-2", 
        "1.0.0-M15-3", 
        "1.0.0-M15-4", 
        "1.0.0-M15-5", 
        "1.0.0-M15-6", 
        "1.0.0-M15-7", 
        "1.0.0-RC1", 
        "1.0.0-RC2", 
        "1.0.0-RC3", 
        "1.0.0-RC4", 
        "1.0.0-RC5", 
        "1.0.0-RC6", 
        "1.0.0-RC7", 
        "1.0.0-RC8", 
        "1.0.0-RC9", 
        "1.0.0-RC10", 
        "1.0.0-RC11", 
        "1.0.0-RC12", 
        "1.0.0-RC12-1", 
        "1.0.0-RC13", 
        "1.0.0-RC14", 
        "1.0.0", 
        "1.0.1-M1", 
        "1.0.1", 
        "1.0.2", 
        "1.0.3", 
        "1.1.0-M1", 
        "1.1.0-M2", 
        "1.1.0-M3", 
        "1.1.0-M4", 
        "1.1.0-M5", 
        "1.1.0-M6", 
        "1.1.0-M7", 
        "1.1.0-M8", 
        "1.1.0-M9", 
        "1.1.0-M10", 
        "1.1.0-M11", 
        "1.1.0-M11-1", 
        "1.1.0-M11-2", 
        "1.1.0-M12", 
        "1.1.0-M13", 
        "1.1.0-M13-1", 
        "1.1.0-M13-2", 
        "1.1.0-M14", 
        "1.1.0-M14-1", 
        "1.1.0-M14-2"
      )

      simple("io.get-coursier::coursier-cache:", expected)
    }

    * - {
      val expected = 32 -> Seq(
        "1.1.0-M1",
        "1.1.0-M2",
        "1.1.0-M3",
        "1.1.0-M4",
        "1.1.0-M5",
        "1.1.0-M6",
        "1.1.0-M7",
        "1.1.0-M8",
        "1.1.0-M9",
        "1.1.0-M10",
        "1.1.0-M11",
        "1.1.0-M11-1",
        "1.1.0-M11-2",
        "1.1.0-M12",
        "1.1.0-M13",
        "1.1.0-M13-1",
        "1.1.0-M13-2",
        "1.1.0-M14",
        "1.1.0-M14-1",
        "1.1.0-M14-2"
      )

      simple("io.get-coursier::coursier-cache:1.1.0-M", expected)
    }

    * - {
      val expected = 32 -> Seq(
        "1.1.0-M14",
        "1.1.0-M14-1",
        "1.1.0-M14-2"
      )

      simple("io.get-coursier::coursier-cache:1.1.0-M14", expected)
    }

    * - simple("io.get-coursier::coursier-cache:1.1.0-M14-2", 32 -> Seq("1.1.0-M14-2"))
  }

}
