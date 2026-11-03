package coursier.clitests

import utest._

abstract class UnsafeWarningTests extends TestSuite {

  def launcher: String
  def isNative: Boolean

  /** Runs a launcher command, and checks it doesn't warn about `sun.misc.Unsafe` on stderr
    *
    * Only native launchers are checked: they embed the JDK they were built with, that warns about
    * it whatever JVM is around. JVM launchers warn or not depending on the JDK running them.
    *
    * Lazy vals of dependencies compiled before Scala 3.8 trigger the warning, so the commands used
    * here have to run some: `version` or `--help` wouldn't do.
    */
  private def checkNoUnsafeWarning(args: String*): Unit =
    if (isNative) {
      val stderr = os.proc(launcher, args).call(stderr = os.Pipe).err.text()
      val unsafeWarnings = stderr
        .linesIterator
        .filter(_.contains("sun.misc.Unsafe"))
        .toVector
      assert(unsafeWarnings.isEmpty)
    }

  val tests = Tests {
    test("resolve") {
      checkNoUnsafeWarning("resolve", "org.scala-lang:scala-library:2.13.18")
    }

    test("fetch") {
      checkNoUnsafeWarning("fetch", "org.scala-lang:scala-library:2.13.18")
    }
  }

}
