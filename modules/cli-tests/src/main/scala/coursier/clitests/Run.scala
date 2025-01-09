package coursier.clitests

import java.util.Locale

import caseapp._
import utest.framework.Result
import utest.TestRunner

import scala.annotation.tailrec

object Run extends CaseApp[RunOptions] {

  @tailrec
  def printThrowable(t: Throwable): Unit =
    if (t != null) {
      println(t.toString)
      for (elem <- t.getStackTrace)
        println(s"  $elem")
      printThrowable(t.getCause)
    }

  def processResults(namePrefix: String, results: Iterator[Result]): Boolean = {

    var anyError = false

    for (res <- results)
      res.value.toEither match {
        case Left(err) =>
          println(Console.RED + namePrefix + res.name + Console.RESET)
          printThrowable(err)
          anyError = true
        case Right(_) =>
        // println(Console.GREEN + namePrefix + res.name + Console.RESET)
      }

    anyError
  }

  def run(options: RunOptions, args: RemainingArgs): Unit = {

    Locale.setDefault(Locale.ENGLISH)

    val launchTests = new LaunchTests {
      val launcher = options.launcher

      def acceptsDOptions = true
      def acceptsJOptions = true
    }
    val aboutTests = new AboutTests {
      val launcher = options.launcher
      def isNative = false

      def acceptsDOptions = true
      def acceptsJOptions = true
    }
    val bootstrapTests = new BootstrapTests {
      val launcher = options.launcher
      val assembly = options.assembly

      def acceptsDOptions = true
      def acceptsJOptions = true
    }
    val fetchTests = new FetchTests {
      val launcher = options.launcher
    }
    val launchResults = TestRunner.runAndPrint(
      launchTests.tests,
      "LaunchTests",
      executor = launchTests
    )
    val aboutResults = TestRunner.runAndPrint(
      aboutTests.tests,
      "AboutTests",
      executor = aboutTests
    )
    val bootstrapResults = TestRunner.runAndPrint(
      bootstrapTests.tests,
      "BootstrapTests",
      executor = bootstrapTests
    )
    val fetchResults = TestRunner.runAndPrint(
      fetchTests.tests,
      "FetchTests",
      executor = fetchTests
    )

    var anyError = false

    anyError = processResults("LaunchTests.", launchResults.leaves) || anyError
    anyError = processResults("AboutTests.", aboutResults.leaves) || anyError
    anyError = processResults("BootstrapTests.", bootstrapResults.leaves) || anyError
    anyError = processResults("FetchTests.", fetchResults.leaves) || anyError

    if (anyError)
      sys.exit(1)
  }
}
