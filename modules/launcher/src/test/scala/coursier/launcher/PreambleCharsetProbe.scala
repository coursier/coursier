package coursier.launcher

/** Prints what a [[Preamble]] holding non-ASCII content encodes to, in this JVM.
  *
  * Meant to be run in a JVM of its own by [[PreambleTests]], which varies the properties that the
  * generated files must not depend on (`file.encoding`, in particular) and compares the bytes it
  * gets back.
  */
object PreambleCharsetProbe {

  /** Only Latin-1 characters, so that any of the code pages Windows consoles run under (437, 850,
    * 1252, …) can represent them - the charset used to write the file is what is under test, not
    * what happens to unmappable characters.
    */
  val value = "héllo wörld"

  val preamble: Preamble = Preamble()
    .addExtraEnvVar("CS_TEST_VALUE", value)

  def hex(bytes: Array[Byte]): String =
    bytes.iterator.map(b => f"${b & 0xff}%02x").mkString

  def unhex(str: String): Array[Byte] =
    str.grouped(2).map(s => Integer.parseInt(s, 16).toByte).toArray

  def main(args: Array[String]): Unit = {
    println("default-charset=" + java.nio.charset.Charset.defaultCharset().name())
    println("bat=" + hex(preamble.withOsKind(true).value))
    println("sh=" + hex(preamble.withOsKind(false).value))
  }
}
