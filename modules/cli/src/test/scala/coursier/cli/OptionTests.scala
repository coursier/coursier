package coursier.cli

import utest._

object OptionTests extends TestSuite {
  val tests = Tests {
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
