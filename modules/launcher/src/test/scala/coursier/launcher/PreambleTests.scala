package coursier.launcher

import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file.Paths

import scala.util.Properties

import utest._

import coursier.launcher.PreambleCharsetProbe.{unhex, value}

object PreambleTests extends TestSuite {

  private def runProbe(extraJavaOpts: String*): Map[String, String] = {
    val javaExe = Paths.get(sys.props("java.home"))
      .resolve("bin")
      .resolve(if (Properties.isWin) "java.exe" else "java")
      .toString
    val output = os.proc(
      javaExe,
      extraJavaOpts,
      "-cp",
      sys.props("java.class.path"),
      "coursier.launcher.PreambleCharsetProbe"
    )
      // the probe only ever prints ASCII, so how we read its output back doesn't matter
      .call(cwd = os.pwd)
      .out.text()
    output
      .linesIterator
      .map(_.trim)
      .filter(_.nonEmpty)
      .map { line =>
        val idx = line.indexOf('=')
        assert(idx > 0)
        line.substring(0, idx) -> line.substring(idx + 1)
      }
      .toMap
  }

  /** The bytes a probe run wrote for the env var value.
    *
    * That value is the only non-ASCII part of either file, hence the only part whose bytes the
    * charset can change - comparing it alone keeps failures readable.
    */
  private def valueBytes(probeOutput: String): Seq[Byte] = {
    val bytes  = unhex(probeOutput)
    val marker = "CS_TEST_VALUE=".getBytes(StandardCharsets.US_ASCII)
    val from   = bytes.indexOfSlice(marker)
    assert(from >= 0)
    bytes
      .drop(from + marker.length)
      .takeWhile(b => b != '"'.toByte && b != '\r'.toByte && b != '\n'.toByte)
      .toSeq
  }

  /** The system OEM code page, read from the registry rather than through the JNI call the code
    * under test goes through. This is the value `GetOEMCP` reports.
    */
  private def oemCodePageFromRegistry(): Option[Int] = {
    val output = os.proc(
      "reg",
      "query",
      "HKLM\\SYSTEM\\CurrentControlSet\\Control\\Nls\\CodePage",
      "/v",
      "OEMCP"
    )
      .call()
      .out.text()
    output
      .linesIterator
      .map(_.trim)
      .filter(_.startsWith("OEMCP"))
      .flatMap(_.split("\\s+").lastOption)
      .flatMap(codePage => scala.util.Try(codePage.toInt).toOption)
      .toSeq
      .headOption
  }

  /** The charset a .bat is expected to be written with here.
    *
    * cmd.exe parses batch files in the OEM code page, so that is what a .bat written on Windows has
    * to use; anywhere else, and whenever the code page cannot be got at or this JVM has no charset
    * for it, UTF-8.
    */
  private lazy val expectedBatCharset: Charset =
    if (Properties.isWin && coursier.paths.Util.useJni())
      oemCodePageFromRegistry()
        .flatMap(codePage => Option(coursier.jniutils.WindowsCodePages.charsetFor(codePage)))
        .getOrElse(StandardCharsets.UTF_8)
    else
      StandardCharsets.UTF_8

  val tests = Tests {

    test("bat is written with the charset cmd.exe parses it with") {
      val bytes = PreambleCharsetProbe.preamble.withOsKind(true).value
      assert(bytes.containsSlice(value.getBytes(expectedBatCharset)))
      if (expectedBatCharset != StandardCharsets.UTF_8)
        // the whole point of not writing these bytes as UTF-8, which is also what the JVM
        // default charset would have given us since JDK 18
        assert(!bytes.containsSlice(value.getBytes(StandardCharsets.UTF_8)))
    }

    test("sh is always written as UTF-8") {
      val bytes = PreambleCharsetProbe.preamble.withOsKind(false).value
      assert(bytes.containsSlice(value.getBytes(StandardCharsets.UTF_8)))
    }

    test("neither depends on the JVM default charset") {
      val asUtf8   = runProbe("-Dfile.encoding=UTF-8")
      val asLatin1 = runProbe("-Dfile.encoding=ISO-8859-1")

      // guards against the comparisons below going vacuous, should a JDK stop letting
      // file.encoding pick the default charset
      val utf8DefaultCharset   = asUtf8("default-charset")
      val latin1DefaultCharset = asLatin1("default-charset")
      assert(utf8DefaultCharset == "UTF-8", latin1DefaultCharset == "ISO-8859-1")

      val batUnderUtf8   = valueBytes(asUtf8("bat"))
      val batUnderLatin1 = valueBytes(asLatin1("bat"))
      val shUnderUtf8    = valueBytes(asUtf8("sh"))
      val shUnderLatin1  = valueBytes(asLatin1("sh"))
      assert(batUnderUtf8 == batUnderLatin1, shUnderUtf8 == shUnderLatin1)

      val sameBatFile = asUtf8("bat") == asLatin1("bat")
      val sameShFile  = asUtf8("sh") == asLatin1("sh")
      assert(sameBatFile, sameShFile)
    }

    test("bat falls back to UTF-8 when Windows cannot be asked") {
      // -Dcoursier.jni=false is the switch users have to turn the JNI calls off
      val withoutJni = runProbe("-Dcoursier.jni=false")
      val batBytes   = valueBytes(withoutJni("bat"))
      assert(batBytes == value.getBytes(StandardCharsets.UTF_8).toSeq)
    }
  }
}
