package coursier.env

import java.io.File
import java.nio.charset.Charset
import java.nio.file.{Files, Path, Paths}

import dataclass.data

@data class ProfileUpdater(
  home: Option[Path] = ProfileUpdater.defaultHome,
  getEnv: Option[String => Option[String]] = Some(k => Option(System.getenv(k))),
  charset: Charset = Charset.defaultCharset(),
  pathSeparator: String = File.pathSeparator
) extends EnvVarUpdater {

  def profileFiles(): Seq[Path] = {

    // https://github.com/rust-lang/rustup.rs/blob/15db63918b9a2b11c302e30b97bf9448e2abd3b9/src/cli/self_update.rs#L1067

    val main = home.toSeq.map(_.resolve(".profile"))

    val zprofile = {
      val isZsh = getEnv.flatMap(_("SHELL")).exists(_.contains("zsh"))
      if (isZsh) {
        val zDotDirOpt = getEnv.flatMap { getEnv0 =>
          getEnv0("ZDOTDIR")
            .flatMap(dir => home.map(_.getFileSystem().getPath(dir)))
            .orElse(home)
        }
        zDotDirOpt.toSeq.map(_.resolve(".zprofile"))
      } else
        Nil
    }

    val bashProfile = home
      .map(_.resolve(".bash_profile"))
      .filter(Files.isRegularFile(_))
      .toSeq

    main ++ zprofile ++ bashProfile
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
        Option(file.getParent).map(Files.createDirectories(_))
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
              Some(content.replaceAllLiterally(addition, ""))
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
        Option(file.getParent).map(Files.createDirectories(_))
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
          s"""export $k="${v.replaceAllLiterally("\"", "\\\"")}"""" + "\n"
      }
      .mkString

    val updates = update
      .pathLikeAppends
      .map {
        case (k, v) =>
          // FIXME Needs more escaping?
          s"""export $k="$$$k$pathSeparator${v.replaceAllLiterally("\"", "\\\"")}"""" + "\n"
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

object ProfileUpdater {
  def defaultHome: Option[Path] =
    Some(Paths.get(System.getProperty("user.home")))
}
