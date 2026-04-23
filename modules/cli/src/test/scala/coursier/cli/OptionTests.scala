package coursier.cli

import utest._

object OptionTests extends TestSuite {
  val tests = Tests {
    /** Verifies the `no duplicated options` scenario behaves as the user expects. */
    test("no duplicated options") {
      for (command <- Coursier.commands) {
        System.err.println(
          s"Checking command ${command.names.map(_.mkString(" ")).mkString(" / ")}"
        )
        command.ensureNoDuplicates()
      }
    }
  }
}
