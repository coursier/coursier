package coursier.cli

import java.io.{File, FileWriter}


trait CliTestLib {

  def withFile(content: String = "",
               fileName: String = "hello",
               suffix: String = "world")(testCode: (File, FileWriter) => Any) {
    val file = File.createTempFile(fileName, suffix) // create the fixture
    val writer = new FileWriter(file)
    writer.write(content)
    writer.flush()
    try {
      testCode(file, writer) // "loan" the fixture to the test
    }
    finally {
      writer.close()
      file.delete()
    }
  }
}
