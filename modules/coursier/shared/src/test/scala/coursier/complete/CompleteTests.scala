package coursier.complete

import coursier.Repositories
import coursier.ivy.IvyRepository
import utest._

import scala.async.Async.{async, await}

object CompleteTests extends TestSuite {

  import coursier.TestHelpers.{ec, cache, handmadeMetadataCache}

  val tests = Tests {

    test("maven") {

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

      test - simple("io.get-c", (0, Seq("io.get-coursier")))
      test - simple("io.get-coursier", (0, Seq("io.get-coursier")))
      test - simple("io.get-coursierz", (0, Nil))

      test {
        val expected = 16 -> Seq(
          "apps",
          "apps-contrib",
          "coursier-bootstrap_2.11",
          "coursier-bootstrap_2.12",
          "coursier-bootstrap_2.13",
          "coursier-bootstrap_2.13.0-M5",
          "coursier-bootstrap_2.13.0-RC1",
          "coursier-cache-java-6_2.10",
          "coursier-cache-java-6_2.11",
          "coursier-cache_2.10",
          "coursier-cache_2.11",
          "coursier-cache_2.12",
          "coursier-cache_2.12.0-RC2",
          "coursier-cache_2.13",
          "coursier-cache_2.13.0-M5",
          "coursier-cache_2.13.0-RC1",
          "coursier-cats-interop_2.11",
          "coursier-cats-interop_2.12",
          "coursier-cats-interop_2.13",
          "coursier-cats-interop_2.13.0-M5",
          "coursier-cats-interop_sjs0.6_2.11",
          "coursier-cats-interop_sjs0.6_2.12",
          "coursier-cats-interop_sjs0.6_2.13",
          "coursier-cats-interop_sjs0.6_2.13.0-M5",
          "coursier-cats-interop_sjs1_2.12",
          "coursier-cats-interop_sjs1_2.13",
          "coursier-cli-graalvm_2.12",
          "coursier-cli-java-6_2.11",
          "coursier-cli-native_0.3_2.12",
          "coursier-cli-native_0.4.0-M2_2.12",
          "coursier-cli-tests_2.12",
          "coursier-cli_2.11",
          "coursier-cli_2.12",
          "coursier-core_2.11",
          "coursier-core_2.12",
          "coursier-core_2.13",
          "coursier-core_2.13.0-M5",
          "coursier-core_2.13.0-RC1",
          "coursier-core_sjs0.6_2.11",
          "coursier-core_sjs0.6_2.12",
          "coursier-core_sjs0.6_2.13",
          "coursier-core_sjs0.6_2.13.0-M5",
          "coursier-core_sjs0.6_2.13.0-RC1",
          "coursier-core_sjs1_2.12",
          "coursier-core_sjs1_2.13",
          "coursier-env_2.12",
          "coursier-env_2.13",
          "coursier-extra_2.10",
          "coursier-extra_2.11",
          "coursier-extra_2.12",
          "coursier-extra_2.13.0-M5",
          "coursier-fetch-js_sjs0.6_2.10",
          "coursier-fetch-js_sjs0.6_2.11",
          "coursier-fetch-js_sjs0.6_2.12",
          "coursier-fetch-js_sjs0.6_2.13",
          "coursier-fetch-js_sjs0.6_2.13.0-M5",
          "coursier-fetch-js_sjs0.6_2.13.0-RC1",
          "coursier-fetch-js_sjs1_2.12",
          "coursier-fetch-js_sjs1_2.13",
          "coursier-install_2.12",
          "coursier-install_2.13",
          "coursier-java-6_2.10",
          "coursier-java-6_2.11",
          "coursier-java-6_sjs0.6_2.10",
          "coursier-java-6_sjs0.6_2.11",
          "coursier-jvm_2.12",
          "coursier-jvm_2.13",
          "coursier-launcher-native_0.3_2.12",
          "coursier-launcher-native_0.4.0-M2_2.12",
          "coursier-launcher-native_0.4_2.12",
          "coursier-launcher_2.11",
          "coursier-launcher_2.12",
          "coursier-launcher_2.13",
          "coursier-okhttp_2.10",
          "coursier-okhttp_2.11",
          "coursier-okhttp_2.12",
          "coursier-okhttp_2.12.0-RC2",
          "coursier-okhttp_2.13",
          "coursier-okhttp_2.13.0-M5",
          "coursier-okhttp_2.13.0-RC1",
          "coursier-publish_2.11",
          "coursier-publish_2.12",
          "coursier-publish_2.13",
          "coursier-sbt-launcher_2.12",
          "coursier-scalaz-interop_2.10",
          "coursier-scalaz-interop_2.11",
          "coursier-scalaz-interop_2.12",
          "coursier-scalaz-interop_2.13",
          "coursier-scalaz-interop_2.13.0-M5",
          "coursier-scalaz-interop_2.13.0-RC1",
          "coursier-scalaz-interop_sjs0.6_2.10",
          "coursier-scalaz-interop_sjs0.6_2.11",
          "coursier-scalaz-interop_sjs0.6_2.12",
          "coursier-scalaz-interop_sjs0.6_2.13",
          "coursier-scalaz-interop_sjs0.6_2.13.0-M5",
          "coursier-scalaz-interop_sjs0.6_2.13.0-RC1",
          "coursier-scalaz-interop_sjs1_2.12",
          "coursier-scalaz-interop_sjs1_2.13",
          "coursier-util_2.11",
          "coursier-util_2.12",
          "coursier-util_2.13",
          "coursier-util_sjs0.6_2.12",
          "coursier-util_sjs0.6_2.13",
          "coursier-util_sjs1_2.12",
          "coursier-util_sjs1_2.13",
          "coursier_2.10",
          "coursier_2.11",
          "coursier_2.12",
          "coursier_2.12.0-RC2",
          "coursier_2.13",
          "coursier_2.13.0-M5",
          "coursier_2.13.0-RC1",
          "coursier_sjs0.6_2.10",
          "coursier_sjs0.6_2.11",
          "coursier_sjs0.6_2.12",
          "coursier_sjs0.6_2.12.0-RC2",
          "coursier_sjs0.6_2.13",
          "coursier_sjs0.6_2.13.0-M5",
          "coursier_sjs0.6_2.13.0-RC1",
          "coursier_sjs1_2.12",
          "coursier_sjs1_2.13",
          "dependency_2.12",
          "dependency_2.13",
          "dependency_3",
          "echo",
          "echo_2.10",
          "echo_2.11",
          "echo_2.12",
          "echo_native0.3_2.11",
          "echo_native0.4.0-M2_2.11",
          "env",
          "fetch-js_sjs1_2.12",
          "fetch-js_sjs1_2.13",
          "http-server-java7_2.10",
          "http-server-java7_2.11",
          "http-server_2.10",
          "http-server_2.11",
          "http-server_2.12",
          "interface",
          "interface-scala-2.12-shaded",
          "interface-svm-subs",
          "interface-svm-subs_2.12",
          "interface-svm-subs_2.13",
          "interface_2.11",
          "interface_2.12",
          "interpolators_2.11",
          "interpolators_2.12",
          "interpolators_2.13",
          "jarjar",
          "jniutils",
          "jvm-index",
          "lm-coursier-shaded_2.12",
          "lm-coursier-shaded_2.13",
          "lm-coursier_2.12",
          "lm-coursier_2.13",
          "props",
          "rules-parser_2.11",
          "rules-parser_2.12",
          "rules-parser_2.13.0-M5",
          "rules-parser_sjs0.6_2.11",
          "rules-parser_sjs0.6_2.12",
          "rules-parser_sjs0.6_2.13.0-M5",
          "sbt",
          "sbt-coursier-java-6_2.10_0.13",
          "sbt-coursier-shared-shaded_2.12_1.0",
          "sbt-coursier-shared_2.12_1.0",
          "sbt-coursier_2.10_0.13",
          "sbt-coursier_2.12_1.0",
          "sbt-coursier_2.12_1.0.0-M5",
          "sbt-coursier_2.12_1.0.0-M6",
          "sbt-cs-publish_2.12_1.0",
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
          "util",
          "versions_2.12",
          "versions_2.13",
          "versions_sjs1_2.12",
          "versions_sjs1_2.13",
          "web_sjs0.6_2.12",
          "web_sjs1_2.12"
        )

        simple("io.get-coursier:", expected)
      }

      test {
        val expected = 17 -> List(
          "coursier-bootstrap",
          "coursier-cache",
          "coursier-cats-interop",
          "coursier-cats-interop_sjs0.6",
          "coursier-cats-interop_sjs1",
          "coursier-cli-graalvm",
          "coursier-cli-native_0.3",
          "coursier-cli-native_0.4.0-M2",
          "coursier-cli-tests",
          "coursier-cli",
          "coursier-core",
          "coursier-core_sjs0.6",
          "coursier-core_sjs1",
          "coursier-env",
          "coursier-extra",
          "coursier-fetch-js_sjs0.6",
          "coursier-fetch-js_sjs1",
          "coursier-install",
          "coursier-jvm",
          "coursier-launcher-native_0.3",
          "coursier-launcher-native_0.4.0-M2",
          "coursier-launcher-native_0.4",
          "coursier-launcher",
          "coursier-okhttp",
          "coursier-publish",
          "coursier-sbt-launcher",
          "coursier-scalaz-interop",
          "coursier-scalaz-interop_sjs0.6",
          "coursier-scalaz-interop_sjs1",
          "coursier-util",
          "coursier-util_sjs0.6",
          "coursier-util_sjs1",
          "coursier",
          "coursier_sjs0.6",
          "coursier_sjs1",
          "dependency",
          "echo",
          "fetch-js_sjs1",
          "http-server",
          "interface-svm-subs",
          "interface",
          "interpolators",
          "lm-coursier-shaded",
          "lm-coursier",
          "rules-parser",
          "rules-parser_sjs0.6",
          "sbt-launcher",
          "versions",
          "versions_sjs1",
          "web_sjs0.6",
          "web_sjs1"
        )
        simple("io.get-coursier::", expected)
      }

      test {
        val expected = 17 -> Seq(
          "coursier-bootstrap",
          "coursier-cache",
          "coursier-cats-interop",
          "coursier-cats-interop_sjs0.6",
          "coursier-cats-interop_sjs1",
          "coursier-cli-graalvm",
          "coursier-cli-native_0.3",
          "coursier-cli-native_0.4.0-M2",
          "coursier-cli-tests",
          "coursier-cli",
          "coursier-core",
          "coursier-core_sjs0.6",
          "coursier-core_sjs1",
          "coursier-env",
          "coursier-extra",
          "coursier-fetch-js_sjs0.6",
          "coursier-fetch-js_sjs1",
          "coursier-install",
          "coursier-jvm",
          "coursier-launcher-native_0.3",
          "coursier-launcher-native_0.4.0-M2",
          "coursier-launcher-native_0.4",
          "coursier-launcher",
          "coursier-okhttp",
          "coursier-publish",
          "coursier-sbt-launcher",
          "coursier-scalaz-interop",
          "coursier-scalaz-interop_sjs0.6",
          "coursier-scalaz-interop_sjs1",
          "coursier-util",
          "coursier-util_sjs0.6",
          "coursier-util_sjs1",
          "coursier",
          "coursier_sjs0.6",
          "coursier_sjs1"
        )
        simple("io.get-coursier::cour", expected)
      }

      test - simple("io.get-coursier::coursier-cache", 17 -> Seq("coursier-cache"))

      test {
        val expected = 16 -> Seq(
          "coursier-cache-java-6_2.10",
          "coursier-cache-java-6_2.11",
          "coursier-cache_2.10",
          "coursier-cache_2.11",
          "coursier-cache_2.12",
          "coursier-cache_2.12.0-RC2",
          "coursier-cache_2.13",
          "coursier-cache_2.13.0-M5",
          "coursier-cache_2.13.0-RC1"
        )
        simple("io.get-coursier:coursier-cache", expected)
      }

      test {
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
          "1.1.0-M14-2",
          "1.1.0-M14-3",
          "1.1.0-M14-4",
          "1.1.0-M14-5",
          "1.1.0-M14-6",
          "1.1.0-M14-7",
          "2.0.0-RC1",
          "2.0.0-RC2",
          "2.0.0-RC2-1",
          "2.0.0-RC2-2",
          "2.0.0-RC2-3",
          "2.0.0-RC2-4",
          "2.0.0-RC2-5",
          "2.0.0-RC2-6",
          "2.0.0-RC3",
          "2.0.0-RC3-1",
          "2.0.0-RC3-2",
          "2.0.0-RC3-3",
          "2.0.0-RC3-4",
          "2.0.0-RC4",
          "2.0.0-RC4-1",
          "2.0.0-RC5",
          "2.0.0-RC5-1",
          "2.0.0-RC5-2",
          "2.0.0-RC5-3",
          "2.0.0-RC5-4",
          "2.0.0-RC5-5",
          "2.0.0-RC5-6",
          "2.0.0-RC6",
          "2.0.0-RC6-1",
          "2.0.0-RC6-2",
          "2.0.0-RC6-3",
          "2.0.0-RC6-4",
          "2.0.0-RC6-5",
          "2.0.0-RC6-6",
          "2.0.0-RC6-7",
          "2.0.0-RC6-8",
          "2.0.0-RC6-9",
          "2.0.0-RC6-10",
          "2.0.0-RC6-11",
          "2.0.0-RC6-12",
          "2.0.0-RC6-13",
          "2.0.0-RC6-14",
          "2.0.0-RC6-15",
          "2.0.0-RC6-16",
          "2.0.0-RC6-17",
          "2.0.0-RC6-18",
          "2.0.0-RC6-19",
          "2.0.0-RC6-20",
          "2.0.0-RC6-21",
          "2.0.0-RC6-22",
          "2.0.0-RC6-23",
          "2.0.0-RC6-24",
          "2.0.0-RC6-25",
          "2.0.0-RC6-26",
          "2.0.0-RC6-27",
          "2.0.0",
          "2.0.1",
          "2.0.2",
          "2.0.3",
          "2.0.4",
          "2.0.5",
          "2.0.6",
          "2.0.7",
          "2.0.8",
          "2.0.9",
          "2.0.10",
          "2.0.11",
          "2.0.12",
          "2.0.13",
          "2.0.14",
          "2.0.15",
          "2.0.16",
          "2.0.16+69-g69cab05e6",
          "2.0.16+73-gddc6d9cc9",
          "2.0.16-156-g18556160e",
          "2.0.16-157-gcef6b2330",
          "2.0.16-158-gbdc8669f9",
          "2.0.16-161-g8a1b8eae5",
          "2.0.16-169-g194ebc55c"
        )

        simple("io.get-coursier::coursier-cache:", expected)
      }

      test {
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
          "1.1.0-M14-2",
          "1.1.0-M14-3",
          "1.1.0-M14-4",
          "1.1.0-M14-5",
          "1.1.0-M14-6",
          "1.1.0-M14-7"
        )

        simple("io.get-coursier::coursier-cache:1.1.0-M", expected)
      }

      test {
        val expected = 32 -> Seq(
          "1.1.0-M14",
          "1.1.0-M14-1",
          "1.1.0-M14-2",
          "1.1.0-M14-3",
          "1.1.0-M14-4",
          "1.1.0-M14-5",
          "1.1.0-M14-6",
          "1.1.0-M14-7"
        )

        simple("io.get-coursier::coursier-cache:1.1.0-M14", expected)
      }

      test - simple("io.get-coursier::coursier-cache:1.1.0-M14-2", 32 -> Seq("1.1.0-M14-2"))
    }

    test("ivy") {

      val repo = IvyRepository.fromPattern(
        "http://ivy.abc.com/" +: coursier.ivy.Pattern.default,
        dropInfoAttributes = true
      )

      val complete = Complete(handmadeMetadataCache)
        .withRepositories(Seq(repo))
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

      test - simple("", 0 -> Seq("com.example", "com.thoughtworks", "test"))
      test - simple("co", 0 -> Seq("com.example", "com.thoughtworks"))
      test - simple("com.ex", 0 -> Seq("com.example"))

      test - simple(
        "com.thoughtworks:",
        17 -> Seq("bug_2.12", "common_2.12", "ivy-maven-publish-fetch_2.12", "top_2.12")
      )
      test - simple("com.thoughtworks:b", 17 -> Seq("bug_2.12"))

      test - simple("com.example:a_2.11:", 19 -> Seq("0.1.0-SNAPSHOT", "0.2.0-SNAPSHOT"))
      test - simple("com.example:a_2.11:0", 19 -> Seq("0.1.0-SNAPSHOT", "0.2.0-SNAPSHOT"))
      test - simple("com.example:a_2.11:0.1", 19 -> Seq("0.1.0-SNAPSHOT"))
    }
  }

}
