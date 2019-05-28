package coursier.publish.sbt

import java.io.{File, OutputStreamWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import coursier.publish.logging.OutputFrame

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

final class Sbt(
  directory: File,
  plugin: File,
  ec: ExecutionContext,
  outputFrameSizeOpt: Option[Int],
  verbosity: Int,
  interactive: Boolean = true
) {

  private implicit val ec0 = ec

  private val keepSbtOutput = (!interactive && verbosity >= 0) || verbosity >= 2

  def run(sbtCommands: String): Try[Int] = {

    val processCommands = Seq("sbt", "-J-Dsbt.log.noformat=true", sbtCommands) // UTF-8…

    if (verbosity >= 2)
      Console.err.println(s"Running ${processCommands.map("'" + _ + "'").mkString(" ")}")

    Try {
      val b = new ProcessBuilder(processCommands.asJava)
      b.directory(directory)
      b.redirectInput(ProcessBuilder.Redirect.PIPE)
      if (keepSbtOutput) {
        b.redirectOutput(ProcessBuilder.Redirect.INHERIT)
        b.redirectError(ProcessBuilder.Redirect.INHERIT)
      } else
        b.redirectErrorStream(true)

      val p = b.start()
      p.getOutputStream.close()
      val outputFrameOpt =
        if (keepSbtOutput || !interactive)
          None
        else
          outputFrameSizeOpt.map { n =>
            System.out.flush()
            System.err.flush()
            val f = new OutputFrame(p.getInputStream, new OutputStreamWriter(System.err), n, Seq("", "--- sbt is running ---"), Seq())
            f.start()
            f
          }

      var retCode = 0
      try {
        retCode = p.waitFor()
      } finally {
        val errStreamOpt = if (retCode == 0) None else Some(System.err)
        outputFrameOpt.foreach(_.stop(keepFrame = false, errored = errStreamOpt))
      }

      retCode
    }
  }


  def publishTo(dir: File, projectsOpt: Option[Seq[String]] = None): Future[Unit] = {

    if (verbosity >= 1)
      Console.err.println(s"Publishing sbt project ${directory.getAbsolutePath} to temporary directory $dir")
    else if (verbosity >= 0) {
      val name = directory.getName
      val msg =
        if (name == ".")
          s"Publishing sbt project to temporary directory"
        else
          s"Publishing ${directory.getName} to temporary directory"

      Console.err.println(msg)
    }

    Console.err.flush()

    // dir escaping a bit meh
    val cmd = Seq(
      s"""apply -cp "${plugin.getAbsolutePath}" sbtcspublish.SbtCsPublishPlugin""",
      "+csPublish \"" + dir + "\""
    ).mkString(";", ";", "")

    run(cmd) match {
      case Success(0) =>
        Future.successful(())
      case Success(n) =>
        Future.failed(new Exception(s"sbt exited with code $n"))
      case Failure(e) =>
        Future.failed(e)
    }
  }

}

object Sbt {

  def isSbtProject(dir: Path): Boolean =
    Files.isDirectory(dir) && {
      val buildProps = dir.resolve("project/build.properties")
      Files.isRegularFile(buildProps) && {
        val contentOpt = Try(new String(Files.readAllBytes(buildProps), StandardCharsets.UTF_8)).toOption
        val sbtVersionLineFound = contentOpt.exists(s => Predef.augmentString(s).lines.exists(_.startsWith("sbt.version=")))
        sbtVersionLineFound
      }
    }

}
