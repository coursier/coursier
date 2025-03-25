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

      val extraArgs = Seq(
        "--variant",
        "org.gradle.jvm.environment=standard-jvm"
      )

      val noBomRes =
        os.proc(launcher, "resolve", extraArgs, dependency).call().out.lines()
      val bomRes =
        os.proc(launcher, "resolve", extraArgs, dependency, "--bom", bom).call().out.lines()

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
  }

}
