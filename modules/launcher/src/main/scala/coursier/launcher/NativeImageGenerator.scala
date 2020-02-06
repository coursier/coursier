package coursier.launcher

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import coursier.launcher.internal.{FileUtil, Windows}

object NativeImageGenerator extends Generator[Parameters.NativeImage] {

  def generate(parameters: Parameters.NativeImage, output: Path): Unit = {

    val startCmd = parameters.graalvmHome match {
      case Some(home) =>
        if (Windows.isWindows)
          Seq(s"$home/bin/native-image.cmd")
        else
          Seq(s"$home/bin/native-image", "--no-server")
      case None =>
        parameters.fetch match {
          case Some(fetch) =>
            val version = parameters.graalvmVersion.getOrElse("latest.release")
            val javaOpts = parameters.graalvmJvmOptions
            val cp = fetch(Seq(s"org.graalvm.nativeimage:svm-driver:$version"))
            // FIXME Really only works well if the JVM is GraalVM
            Seq("java") ++ javaOpts ++ Seq("-cp", cp.map(_.getAbsolutePath).mkString(File.pathSeparator), "com.oracle.svm.driver.NativeImage")
          case None =>
            sys.error("No GraalVM home specified, and no function to fetch artifacts passed either.")
        }
    }

    def generate(extraArgs: String*): Either[Int, Unit] = {
      val cp = parameters.jars
        .map(_.getAbsolutePath.toString)
        .mkString(File.pathSeparator)
      val cmd = startCmd ++
        parameters.graalvmOptions ++
        parameters.nameOpt.map(name => s"-H:Name=$name") ++
        extraArgs ++
        Seq("-cp", cp, parameters.mainClass, output.toString)
      if (parameters.verbosity >= 1)
        System.err.println(s"Running $cmd")
      val b = new ProcessBuilder(cmd: _*)
        .inheritIO()
      val p = b.start()
      val retCode = p.waitFor()
      if (retCode == 0) {
        if (Windows.isWindows) {
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
    }

    val res = generate()

    res match {
      case Left(retCode) =>
        sys.error(s"Error running native-image (exit code: $retCode)")
      case Right(()) =>
        FileUtil.tryMakeExecutable(output)
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
