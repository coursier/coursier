package coursier.cli

import java.nio.charset.StandardCharsets

import utest._

object NativeImagePropertiesTests extends TestSuite {

  private val resourcePath =
    "META-INF/native-image/io.get-coursier/coursier-cli/native-image.properties"

  private lazy val content: String = {
    val is = Thread.currentThread().getContextClassLoader.getResourceAsStream(resourcePath)
    assert(is != null)
    try new String(is.readAllBytes(), StandardCharsets.UTF_8)
    finally is.close()
  }

  val tests = Tests {
    test("native image launchers get a UTF-8 encoding") {
      // GraalVM freezes the encoding properties into the image at build time, from the locale
      // or code page of whichever machine built it. Pinning them here is what keeps a launcher
      // reading arguments and writing files as UTF-8, wherever it was built and wherever it
      // ends up running.
      val missing = Seq("file.encoding", "stdout.encoding", "stderr.encoding")
        .filterNot(prop => content.contains(s"-D$prop=UTF-8"))
      assert(missing.isEmpty)
    }
  }
}
