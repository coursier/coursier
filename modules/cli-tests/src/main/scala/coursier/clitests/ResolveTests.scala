package coursier.clitests

import utest._

abstract class ResolveTests extends TestSuite {

  def launcher: String

  val tests = Tests {
    test("rules") {
      val proc = os.proc(
        launcher,
        "resolve",
        "-r",
        "jitpack",
        "sh.almond:scala-kernel_2.12.8:0.3.0",
        "--rule",
        "SameVersion(com.fasterxml.jackson.core:jackson-*)"
      )
      val output = proc.call().out.text()
      val jacksonOutput = output
        .linesIterator
        .filter(_.startsWith("com.fasterxml.jackson.core:jackson-"))
        .toVector
      val expectedJacksonOutput = Seq(
        "com.fasterxml.jackson.core:jackson-annotations:2.9.6:default",
        "com.fasterxml.jackson.core:jackson-core:2.9.6:default",
        "com.fasterxml.jackson.core:jackson-databind:2.9.6:default"
      )
      assert(jacksonOutput == expectedJacksonOutput)
    }

    test("BOM") {
      val dependency = "ch.epfl.scala:bsp4j:2.2.0-M2"
      val bom        = "io.quarkus:quarkus-bom:3.16.2"
      val gsonModule = "com.google.code.gson:gson"

      val noBomRes =
        os.proc(launcher, "resolve", dependency).call().out.lines()
      val bomRes =
        os.proc(launcher, "resolve", dependency, "--bom", bom).call().out.lines()

      def gsonVersion(results: Seq[String]) =
        results
          .find(_.startsWith(gsonModule + ":"))
          .map(_.stripPrefix(gsonModule + ":"))
          .map(_.split(":").apply(0))
          .getOrElse(sys.error(s"$gsonModule not found in $results"))

      val noBomGsonVersion = gsonVersion(noBomRes)
      val bomGsonVersion   = gsonVersion(bomRes)

      // without a BOM, the gson version is picked from [2.9.1,2.11)
      assert(noBomGsonVersion.startsWith("2.9.") || noBomGsonVersion.startsWith("2.10."))

      val expectedBomGsonVersion = "2.11.0"
      assert(bomGsonVersion == expectedBomGsonVersion)
    }

    test("Gradle Module") {
      def check(dependency: String, onlyInvolvesModules: Boolean = false): Unit =
        TestUtil.withTempDir { tmpDir0 =>
          val tmpDir = os.Path(tmpDir0)

          val moduleArgs = Seq(
            "--enable-gradle-modules",
            "--variant",
            "org.jetbrains.kotlin.platform.type=js",
            "--variant",
            "org.jetbrains.kotlin.js.compiler=ir"
          )

          // download POMs via a fresh cache
          val basePomRes =
            os.proc(launcher, "resolve", dependency, "--cache", tmpDir / "pom-cache")
              .call().out.lines()
          // download modules via a fresh cache
          val baseModuleRes =
            os.proc(launcher, "resolve", dependency, "--cache", tmpDir / "module-cache", moduleArgs)
              .call().out.lines()
          // download modules in a cache that already has the POMs
          val moduleAfterPomRes =
            os.proc(launcher, "resolve", dependency, "--cache", tmpDir / "pom-cache", moduleArgs)
              .call().out.lines()

          assert(basePomRes.forall(!_.contains("{")))
          assert(basePomRes.forall(!_.contains("}")))

          if (onlyInvolvesModules) {
            assert(baseModuleRes.forall(_.contains("{")))
            assert(baseModuleRes.forall(_.contains("}")))
          }
          else {
            assert(baseModuleRes.exists(_.contains("{")))
            assert(baseModuleRes.exists(_.contains("}")))
          }

          if (baseModuleRes != moduleAfterPomRes) {
            pprint.err.log(basePomRes)
            pprint.err.log(baseModuleRes)
            pprint.err.log(moduleAfterPomRes)
          }
          assert(baseModuleRes == moduleAfterPomRes)
        }

      test("kotlinx-html-js") {
        check("org.jetbrains.kotlinx:kotlinx-html-js:0.11.0", onlyInvolvesModules = true)
      }
      test("kotlin-compiler") {
        check("org.jetbrains.kotlin:kotlin-compiler:1.9.24")
      }
    }
  }

}
