package coursier.clitests

import utest._

abstract class ResolveTests extends TestSuite {

  def launcher: String

  val tests = Tests {
    test("rules") {
      val output = LauncherTestUtil.output(
        launcher,
        "resolve",
        "-r", "jitpack",
        "sh.almond:scala-kernel_2.12.8:0.3.0",
        "--rule", "SameVersion(com.fasterxml.jackson.core:jackson-*)"
      )
      val jacksonOutput = output
        .split(System.lineSeparator())
        .filter(_.startsWith("com.fasterxml.jackson.core:jackson-"))
        .toSeq
      val expectedJacksonOutput = Seq(
        "com.fasterxml.jackson.core:jackson-annotations:2.9.6:default",
        "com.fasterxml.jackson.core:jackson-core:2.9.6:default",
        "com.fasterxml.jackson.core:jackson-databind:2.9.6:default"
      )
      assert(jacksonOutput == expectedJacksonOutput)
    }
  }

}
