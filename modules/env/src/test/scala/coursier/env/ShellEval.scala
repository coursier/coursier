package coursier.env

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import scala.util.Properties
import scala.util.control.NonFatal

/** Runs generated snippets with actual shells, to check what they evaluate to */
object ShellEval {

  /** Values that run a command (creating a `pwned*` file) if not escaped properly */
  val nastyValues: Seq[String] = Seq(
    "/jvm/jdk$(touch pwned1)",
    "/jvm/jdk`touch pwned2`",
    "/jvm/jdk\"; touch pwned3; echo \"",
    "/jvm/jdk\\\"; touch pwned4; echo \\\"",
    "/jvm/jdk'; touch pwned5; echo '",
    "/jvm/jdk$HOME ${PATH} $((1+1)) \\ \\\\ \\n * ? ~ & | ; < > ! %PATH% \u2019",
    "/jvm/jdk\\"
  )

  private lazy val availableShells: Seq[String] =
    if (Properties.isWin) Nil
    else
      Seq("sh", "bash", "zsh").filter { shell =>
        try new ProcessBuilder(shell, "-c", "true").start().waitFor() == 0
        catch {
          case NonFatal(_) => false
        }
      }

  /** Shells that are available on this machine, among sh, bash, and zsh */
  def shells: Seq[String] =
    availableShells

  /** Sources `script` with `shell`, then prints the value of `variable`
    *
    * @return
    *   the value of `variable`, and the files created in the working directory of the shell
    */
  def eval(
    shell: String,
    script: String,
    variable: String,
    env: Map[String, String] = Map.empty
  ): (String, Seq[String]) = {
    val dir = Files.createTempDirectory("coursier-env-test")
    try {
      val fullScript = script + "\n" + "printf '%s' \"$" + variable + "\"\n"
      val scriptFile = Files.createTempFile("coursier-env-test", ".sh")
      val outputFile = Files.createTempFile("coursier-env-test", ".txt")
      try {
        Files.write(scriptFile, fullScript.getBytes(StandardCharsets.UTF_8))
        val b = new ProcessBuilder(shell, scriptFile.toString)
          .directory(dir.toFile)
          .redirectInput(ProcessBuilder.Redirect.PIPE)
          .redirectOutput(outputFile.toFile)
          .redirectError(ProcessBuilder.Redirect.INHERIT)
        for ((k, v) <- env)
          b.environment().put(k, v)
        val p = b.start()
        p.getOutputStream.close()
        val retCode = p.waitFor()
        if (retCode != 0)
          sys.error(s"$shell exited with code $retCode when running:\n$fullScript")
        val output  = new String(Files.readAllBytes(outputFile), StandardCharsets.UTF_8)
        val created = dir.toFile.list().toSeq.sorted
        (output, created)
      }
      finally {
        Files.deleteIfExists(scriptFile)
        Files.deleteIfExists(outputFile)
      }
    }
    finally deleteRecursively(dir.toFile)
  }

  private def deleteRecursively(f: File): Unit = {
    Option(f.listFiles()).toSeq.flatten.foreach(deleteRecursively)
    f.delete()
  }

}
