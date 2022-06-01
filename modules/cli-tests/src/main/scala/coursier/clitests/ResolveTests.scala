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
  }

}
