package coursier.cli.app

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import coursier.bootstrap.LauncherBat

object GraalvmNativeImage {

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

  def create(
    graalvmParams: GraalvmParams,
    graalvmOptions: Seq[String],
    shellPrependOptions: Seq[String],
    tmpDest: Path,
    dest: Path,
    jars: Seq[Path],
    nameOpt: Option[String],
    mainClass: String,
    reflectionConfOpt: Option[String],
    verbosity: Int
  ): Either[Int, Unit] = {

    val imageDest =
      if (shellPrependOptions.isEmpty || LauncherBat.isWindows)
        tmpDest
      else
        dest.getParent.resolve(s".${dest.getFileName}.binary")

    def generate(extraArgs: String*): Either[Int, Unit] = {
      val cp = jars
        .map(_.toAbsolutePath.toString)
        .mkString(File.pathSeparator)
      val startCmd =
        if (LauncherBat.isWindows)
          Seq(s"${graalvmParams.home}/bin/native-image.cmd")
        else
          Seq(s"${graalvmParams.home}/bin/native-image", "--no-server")
      val cmd = startCmd ++
        graalvmOptions ++
        graalvmParams.extraNativeImageOptions ++
        nameOpt.map(name => s"-H:Name=$name") ++
        extraArgs ++
        Seq("-cp", cp, mainClass, imageDest.toString)
      if (verbosity >= 1)
        System.err.println(s"Running $cmd")
      val b = new ProcessBuilder(cmd: _*)
        .inheritIO()
      val p = b.start()
      val retCode = p.waitFor()
      if (retCode == 0) {
        if (LauncherBat.isWindows) {
          val exe = imageDest.getFileName.toString + ".exe"

          import scala.collection.JavaConverters._
          val s = Files.list(imageDest.getParent)
          val prefix = imageDest.getFileName + "."
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

    val res = reflectionConfOpt match {
      case None =>
        generate()
      case Some(conf) =>
        withTempFile(conf.getBytes(StandardCharsets.UTF_8)) { confFile =>
          generate(s"-H:ReflectionConfigurationFiles=${confFile.toAbsolutePath}")
        }
    }

    res.map { _ =>
      if (shellPrependOptions.nonEmpty) {
        // https://stackoverflow.com/a/246128/3714539
        val launcher =
          s"""#!/usr/bin/env bash
             |set -u
             |DIR="$$( cd "$$( dirname "$${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
             |exec "$$DIR/${imageDest.getFileName}" ${shellPrependOptions.mkString(" ")} "$$@"
             |""".stripMargin
        Files.write(tmpDest, launcher.getBytes(StandardCharsets.UTF_8))
        coursier.bootstrap.util.FileUtil.tryMakeExecutable(tmpDest)
      }
    }
  }

}
