package coursier.env

import java.io.File
import java.nio.charset.Charset
import java.nio.file.{Files, Path, Paths}

import dataclass.data
import java.nio.file.FileAlreadyExistsException

@data class FishUpdater(
  home: Option[Path] = FishUpdater.defaultHome,
  getEnv: Option[String => Option[String]] = Some(k => Option(System.getenv(k))),
  charset: Charset = Charset.defaultCharset(),
  pathSeparator: String = File.pathSeparator
) extends EnvVarUpdater {

  def profileFiles(): Seq[Path] = {

    val fishprofile = {
      val isFish = getEnv.flatMap(_("SHELL")).exists(_.contains("fish"))
      if (isFish) {
        val configFish = getEnv.map(env => 
          env("XDG_CONFIG_HOME").getOrElse(env("HOME").map(_ + "/.config").get) + "/fish/config.fish").get
        home.map(_.getFileSystem().getPath(configFish)).toSeq
      } else {
        Nil
      }
    }

    fishprofile
  }

  private def startEndIndices(start: String, end: String, content: String): Option[(Int, Int)] = {
    val startIdx = content.indexOf(start)
    if (startIdx >= 0) {
      val endIdx = content.indexOf(end, startIdx + 1)
      if (endIdx >= 0)
        Some(startIdx, endIdx + end.length)
      else
        None
    } else
      None
  }

  private def addToProfileFiles(addition: String, titleOpt: Option[String]): Boolean = {

    def updated(content: String): Option[String] =
      titleOpt match {
        case None =>
          if (content.contains(addition))
            None
          else
            Some {
              content + "\n" +
                addition.stripSuffix("\n") + "\n"
            }
        case Some(title) =>
          val start = s"# >>> $title >>>\n"
          val end = s"# <<< $title <<<\n"
          val withTags = "\n" +
            start +
            addition.stripSuffix("\n") + "\n" +
            end
          if (content.contains(withTags))
            None
          else
            Some {
              startEndIndices(start, end, content) match {
                case None =>
                  content + withTags
                case Some((startIdx, endIdx)) =>
                  content.take(startIdx) +
                    withTags +
                    content.drop(endIdx)
              }
            }
      }

    var updatedSomething = false
    for (file <- profileFiles()) {
      val contentOpt = Some(file)
        .filter(Files.exists(_))
        .map(f => new String(Files.readAllBytes(f), charset))
      for (updatedContent <- updated(contentOpt.getOrElse(""))) {
        Option(file.getParent).map(FishUpdater.createDirectories(_))
        Files.write(file, updatedContent.getBytes(charset))
        updatedSomething = true
      }
    }
    updatedSomething
  }

  private def removeFromProfileFiles(additionOpt: Option[String], titleOpt: Option[String]): Boolean = {

    def updated(content: String): Option[String] =
      titleOpt match {
        case None =>
          additionOpt.flatMap { addition =>
            if (content.contains(addition))
              Some(content.replace(addition, ""))
            else
              None
          }
        case Some(title) =>
          val start = s"# >>> $title >>>\n"
          val end = s"# <<< $title <<<\n"
          startEndIndices(start, end, content).map {
            case (startIdx, endIdx) =>
              content.take(startIdx).stripSuffix("\n") +
                content.drop(endIdx)
          }
      }

    var updatedSomething = false
    for (file <- profileFiles()) {
      val contentOpt = Some(file)
        .filter(Files.exists(_))
        .map(f => new String(Files.readAllBytes(f), charset))
      for (updatedContent <- updated(contentOpt.getOrElse(""))) {
        Option(file.getParent).map(FishUpdater.createDirectories(_))
        Files.write(file, updatedContent.getBytes(charset))
        updatedSomething = true
      }
    }
    updatedSomething
  }

  private def contentFor(update: EnvironmentUpdate): String = {

    val set = update
      .set
      .map {
        case (k, v) =>
          // FIXME Needs more escaping?
          s"""set -gx $k "${v.replace("\"", "\\\"")}"""" + "\n"
      }
      .mkString

    val updates = update
      .pathLikeAppends
      .map {
        case (k, v) =>
          // FIXME Needs more escaping?
          s"""set -gx $k $$$k ${v.replace(";", " ").replace("\"", "\\\"")}"""" + "\n"
      }
      .mkString

    set + updates
  }

  def applyUpdate(update: EnvironmentUpdate): Boolean =
    applyUpdate(update, None)
  def applyUpdate(update: EnvironmentUpdate, title: String): Boolean =
    applyUpdate(update, Some(title))
  def applyUpdate(update: EnvironmentUpdate, title: Option[String]): Boolean = {
    val addition = contentFor(update)
    addToProfileFiles(addition, title)
  }

  def tryRevertUpdate(update: EnvironmentUpdate): Boolean = {
    val addition = contentFor(update)
    removeFromProfileFiles(Some(addition), None)
  }
  def tryRevertUpdate(title: String): Boolean = {
    removeFromProfileFiles(None, Some(title))
  }

}

object FishUpdater {
  def defaultHome: Option[Path] =
    Some(Paths.get(System.getProperty("user.home")))

  private[env] def createDirectories(path: Path): Unit =
    try Files.createDirectories(path)
    catch {
      case _: FileAlreadyExistsException if Files.isDirectory(path) =>
        // Ignored, see https://bugs.openjdk.java.net/browse/JDK-8130464
    }

}
