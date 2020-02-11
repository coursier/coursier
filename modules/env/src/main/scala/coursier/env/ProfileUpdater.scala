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
      val isZsh = getEnv.exists(_("SHELL").contains("zsh"))
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

  private def addToProfileFiles(addition: String): Boolean = {
    var updatedSomething = false
    for (file <- profileFiles()) {
      val contentOpt = Some(file)
        .filter(Files.exists(_))
        .map(f => new String(Files.readAllBytes(f), charset))
      if (!contentOpt.exists(_.contains(addition))) {
        val updatedContent = contentOpt.getOrElse("") + addition
        Option(file.getParent).map(Files.createDirectories(_))
        Files.write(file, updatedContent.getBytes(charset))
        updatedSomething = true
      }
    }
    updatedSomething
  }

  def addPath(dir: String*): Unit =
    if (dir.nonEmpty) {
      val update = EnvironmentUpdate()
        .withPathLikeAppends(Seq("PATH" -> dir.mkString(pathSeparator)))
      applyUpdate(update, "added by coursier setup")
    }

  def setJavaHome(dir: String): Unit = {
    val update = EnvironmentUpdate().withSet(Seq("JAVA_HOME" -> dir))
    applyUpdate(update, "added by coursier setup")
  }

  def applyUpdate(update: EnvironmentUpdate): Boolean =
    applyUpdate(update, None)
  def applyUpdate(update: EnvironmentUpdate, headerComment: String): Boolean =
    applyUpdate(update, Some(headerComment))
  def applyUpdate(update: EnvironmentUpdate, headerComment: Option[String]): Boolean = {

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

    val addition = "\n" +
      headerComment
        .iterator
        .flatMap(_.linesIterator)
        .map("# " + _ + "\n")
        .mkString +
      set +
      updates

    addToProfileFiles(addition)
  }

}

object ProfileUpdater {
  def defaultHome: Option[Path] =
    Some(Paths.get(System.getProperty("user.home")))
}
