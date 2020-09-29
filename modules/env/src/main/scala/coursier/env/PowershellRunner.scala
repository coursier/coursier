package coursier.env

import java.nio.charset.StandardCharsets
import java.util.Base64
import dataclass.data
import java.io.InputStream
import java.io.ByteArrayOutputStream
import scala.util.Try

@data class PowershellRunner(
  powershellExePath: String = "powershell.exe",
  options: Seq[String] = PowershellRunner.defaultOptions,
  encodeProgram: Boolean = true
) {

  def runScript(script: String): String = {

    // inspired by https://github.com/soc/directories-jvm/blob/1f344ef0087e8422f6c7334317e73b8763d9e483/src/main/java/io/github/soc/directories/Util.java#L147
    val fullScript = "& {\n" +
      "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\n" +
      script +
      "\n}"

    val scriptArgs =
      if (encodeProgram) {
        val base64 = Base64.getEncoder()
        val encodedScript = base64.encodeToString(fullScript.getBytes(StandardCharsets.UTF_16LE))
        Seq("-EncodedCommand", encodedScript)
      } else
        Seq("-Command", fullScript)

    val command = Seq(powershellExePath) ++ options ++ scriptArgs

    val b = new ProcessBuilder(command: _*)
      .redirectInput(ProcessBuilder.Redirect.PIPE)
      .redirectOutput(ProcessBuilder.Redirect.PIPE)
      .redirectError(ProcessBuilder.Redirect.INHERIT)
    val p: Process = b.start()
    p.getOutputStream.close()
    val outputBytes = PowershellRunner.readFully(p.getInputStream)
    val retCode = p.waitFor()
    if (retCode == 0)
      new String(outputBytes, StandardCharsets.UTF_8)
    else
      throw new Exception(
        s"Error running powershell script (exit code: $retCode)" + System.lineSeparator() +
          s"Error running command:" + System.lineSeparator() +
          command.map("  " + _ + System.lineSeparator()).mkString + System.lineSeparator() +
          "Output:" + System.lineSeparator() +
          Try(new String(outputBytes, StandardCharsets.UTF_8))
            .toOption
            .getOrElse("[Could not convert output]")
      )
  }

}

object PowershellRunner {

  def defaultOptions: Seq[String] =
    Seq("-NoProfile", "-NonInteractive")

  private def readFully(is: InputStream): Array[Byte] = {
    val buffer = new ByteArrayOutputStream
    val data = Array.ofDim[Byte](16384)

    var nRead = 0
    while ({
      nRead = is.read(data, 0, data.length)
      nRead != -1
    })
      buffer.write(data, 0, nRead)

    buffer.flush()
    buffer.toByteArray
  }


}
