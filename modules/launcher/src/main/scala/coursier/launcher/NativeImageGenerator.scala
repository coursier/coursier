package coursier.launcher

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

import coursier.launcher.internal.FileUtil

object NativeImageGenerator extends Generator[Parameters.NativeImage] {

  // TODO Get some kind of CommandRunner via the params?
  // That would allow to unit test the logic here.

  private def executable(dir: File, name: String, pathExtensionsOpt: Option[Seq[String]]): Option[File] =
    pathExtensionsOpt match {
      case None =>
        Some(new File(dir, name))
      case Some(pathExts) =>
        pathExts
          .toStream
          .map(ext => new File(dir, s"$name$ext"))
          .filter(_.exists())
          .headOption
    }


  def generate(parameters: Parameters.NativeImage, output: Path): Unit = {

    // more concise graalvm logging
    val relativizedOutput =
      if (output.isAbsolute) {
        val currentDir = Paths.get(System.getProperty("user.dir"))
        if (output.startsWith(currentDir))
          currentDir.relativize(output)
        else
          output
      } else
        output

    val startCmd = {
      val version = parameters.graalvmVersion.getOrElse("latest.release")
      val isInterval = version.startsWith("latest") || version.endsWith("+") || version.contains("[") || version.contains("(")
      val version0 = if (isInterval) version else version + "+"
      val javaOpts = parameters.graalvmJvmOptions
      val cp = parameters.fetch(Seq(s"org.graalvm.nativeimage:svm-driver:$version0"))
      // Really only works well if the JVM is GraalVM
      val javaPath = parameters.javaHome
        .map(new File(_, "bin"))
        .flatMap(executable(_, "java", parameters.windowsPathExtensions))
        .fold("java")(_.getAbsolutePath)
      Seq(javaPath) ++ javaOpts ++ Seq("-cp", cp.map(_.getAbsolutePath).mkString(File.pathSeparator), "com.oracle.svm.driver.NativeImage")
    }

    var tmpFile: Path = null

    val res = try {
      val cp =
        if (parameters.intermediateAssembly) {
          val p = Parameters.Assembly()
            .withFiles(parameters.jars)
            .withMainClass(parameters.mainClass)
            .withPreambleOpt(None)
          tmpFile = Files.createTempFile("native-image-assembly-", ".jar")
          AssemblyGenerator.generate(p, tmpFile)
          tmpFile.toString
        } else
          parameters.jars
            .map(_.getAbsolutePath.toString)
            .mkString(File.pathSeparator)

      val cmd = startCmd ++
        parameters.graalvmOptions ++
        parameters.nameOpt.map(name => s"-H:Name=$name") ++
        Seq("-cp", cp, parameters.mainClass, relativizedOutput.toString)
      if (parameters.verbosity >= 1)
        System.err.println(s"Running $cmd")
      val b = new ProcessBuilder(cmd: _*)
        .inheritIO()
      val p = b.start()
      val retCode = p.waitFor()
      if (retCode == 0) {
        if (parameters.isWindows) {
          val exe = output.getFileName.toString + ".exe"

          import scala.collection.JavaConverters._
          val s = Files.list(output.getParent)
          val prefix = output.getFileName + "."
          s.iterator().asScala.toVector.foreach { p =>
            val name = p.getFileName.toString
            if (name != exe && name.startsWith(prefix))
              Files.deleteIfExists(p)
          }
          s.close()
        }

        Right(())
      } else
        Left(retCode)
    } finally {
      if (tmpFile != null)
        Files.deleteIfExists(tmpFile)
    }

    res match {
      case Left(retCode) =>
        sys.error(s"Error running native-image (exit code: $retCode)")
      case Right(()) =>
    }
  }

  private def withTempFile[T](content: Array[Byte])(t: Path => T): T = {

    val path = Files.createTempFile("temp", ".tmp")

    try {
      Files.write(path, content)
      t(path)
    }
    finally {
      Files.deleteIfExists(path)
    }
  }

}
