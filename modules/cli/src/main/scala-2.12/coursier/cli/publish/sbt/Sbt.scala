package coursier.cli.publish.sbt

import java.io.{File, OutputStreamWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

import coursier.cli.publish.logging.OutputFrame

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.xml.XML

final class Sbt(
  directory: File,
  plugin: File,
  ec: ExecutionContext,
  outputFrameSizeOpt: Option[Int],
  verbosity: Int
) {

  private implicit val ec0 = ec

  private val keepSbtOutput = verbosity >= 2

  def run(sbtCommands: String): Try[Int] = {

    // still getting some ANSI stuff in spite of -batch and closing stdin…
    val processCommands = Seq("sbt", "-batch", sbtCommands) // UTF-8…

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
        if (keepSbtOutput)
          None
        else
          outputFrameSizeOpt.map { n =>
            System.out.flush()
            System.err.flush()
            val f = new OutputFrame(p.getInputStream, new OutputStreamWriter(System.err), n, Seq("", "--- sbt is running ---"), Seq())
            f.start()
            f
          }

      try p.waitFor()
      finally {
        outputFrameOpt.foreach(_.stop(keepFrame = false))
      }
    }
  }


  def structure(dest: Path = null) = {

    // blocking (shortly, but still)
    val (dest0, deleteFile) =
      Option(dest)
        .map((_, false))
        .getOrElse {
          val f = Files.createTempFile("coursier-publish-sbt-structure", ".xml")
          (f, true)
        }

    val optString = "prettyPrint"

    val setCommands = Seq(
      s"""shellPrompt := { _ => "" }""",
      s"""SettingKey[_root_.scala.Option[_root_.sbt.File]]("sbtStructureOutputFile") in _root_.sbt.Global := _root_.scala.Some(_root_.sbt.file("$dest0"))""",
      s"""SettingKey[_root_.java.lang.String]("sbtStructureOptions") in _root_.sbt.Global := "$optString""""
    ).mkString("set _root_.scala.collection.Seq(", ",", ")")

    val sbtCommands = Seq(
      setCommands,
      s"""apply -cp "${plugin.getAbsolutePath}" org.jetbrains.sbt.CreateTasks""",
      s"*/*:dumpStructure"
    ).mkString(";", ";", "")

    Future {

      if (verbosity >= 0) {
        val name =
          if (verbosity >= 1) directory.getAbsolutePath
          else directory.getName
        val msg =
          if (name == ".")
            "Extracting sbt structure"
          else
            s"Extracting sbt structure of $name"
        Console.err.println(msg)
      }

      run(sbtCommands) match {
        case Success(0) =>
          Future {
            try new String(java.nio.file.Files.readAllBytes(dest0), "UTF-8")
            finally {
              if (deleteFile)
                Files.deleteIfExists(dest0)
            }
          }
        case Success(n) =>
          Future.failed(new Exception(s"sbt exited with code $n"))
        case Failure(e) =>
          Future.failed(e)
      }
    }.flatten
  }

  def projects() =
    structure().map { s =>
      val x = XML.loadString(s)
      x.child.collect {
        case n if n.label == "project" =>
          n.child.find(_.label == "id").map(_.text)
      }.flatten
    }

  def publishTo(dir: File, projectsOpt: Option[Seq[String]] = None) =
    for {
      projs <- projectsOpt.map(Future.successful).getOrElse {
        projects()
      }
      cmd = {

        val setCommands = projs.map { p =>
          s"""_root_.sbt.Keys.publishTo in _root_.sbt.LocalProject("$p") := """ +
            s"""_root_.scala.Some("tmp" at "${dir.getAbsoluteFile.toURI.toASCIIString}")"""
        }.mkString("set _root_.scala.collection.Seq(", ",", ")")

        Seq(setCommands, "publish").mkString("; ", "; ", "")
      }
      _ = {
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
      }
      _ <- run(cmd) match {
        case Success(0) =>
          Future.successful(())
        case Success(n) =>
          Future.failed(new Exception(s"sbt exited with code $n"))
        case Failure(e) =>
          Future.failed(e)
      }
    } yield ()

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
