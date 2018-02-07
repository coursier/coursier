package coursier.cli

import java.io.{File, FileWriter}


trait CliTestLib {

  def withFile(content: String = "")(testCode: (File, FileWriter) => Any) {
    val file = File.createTempFile("hello", "world") // create the fixture
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


trait TestOnlyExtraArgsApp extends caseapp.core.DefaultArgsApp {
  private var remainingArgs1 = Seq.empty[String]
  private var extraArgs1 = Seq.empty[String]

  override def setRemainingArgs(remainingArgs: Seq[String], extraArgs: Seq[String]): Unit = {
    remainingArgs1 = remainingArgs
  }

  override def remainingArgs: Seq[String] = remainingArgs1

  def extraArgs: Seq[String] =
    extraArgs1
}
