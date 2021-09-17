package coursier.jvm

import java.nio.file.{FileSystem, FileSystems, Path}
import java.util.regex.Pattern.quote

import coursier.core.Module
import coursier.parse.{DependencyParser, JavaOrScalaDependency, JavaOrScalaModule, ModuleParser}
import dataclass.data

// FIXME Initially copied from coursier.install.Channel, there's some duplication with it…

sealed abstract class JvmChannel extends Product with Serializable {
  def repr: String
}

object JvmChannel {

  @data class FromModule(
    module: Module,
    version: String = "latest.release"
  ) extends JvmChannel {
    def repr: String =
      module.repr
  }

  @data class FromUrl(url: String) extends JvmChannel {
    def repr: String =
      url
  }

  @data class FromFile(path: Path) extends JvmChannel {
    def repr: String =
      path.toString
  }

  def module(module: Module): FromModule =
    FromModule(module)
  def module(module: Module, version: String): FromModule =
    FromModule(module, version)

  private lazy val ghUrlMatcher =
    (quote("https://github.com/") + "([^/]*)/([^/]*)" + quote("/blob/") + "([^/]*)" + quote(
      "/"
    ) + "(.*)").r.pattern

  private def defaultGhFileName = "apps.json"
  private def defaultGhPath     = defaultGhFileName
  private def defaultGhBranch   = "master"

  private def ghUrl(org: String, name: String, branch: String, path: String): String =
    s"https://raw.githubusercontent.com/$org/$name/$branch/$path"

  def url(url: String): FromUrl = {

    val m = ghUrlMatcher.matcher(url)

    val url0 =
      if (m.matches()) {
        val org    = m.group(1)
        val name   = m.group(2)
        val branch = m.group(3)
        val path   = m.group(4)
        ghUrl(org, name, branch, path)
      }
      else
        url

    // https://github.com/coursier/apps/blob/master/apps/resources/ammonite.json
    // https://raw.githubusercontent.com/coursier/apps/master/apps/resources/ammonite.json

    FromUrl(url0)
  }

  def parse(s: String): Either[String, JvmChannel] =
    parse(s, FileSystems.getDefault)

  def parse(s: String, fs: FileSystem): Either[String, JvmChannel] =
    if (s.contains("://"))
      Right(JvmChannel.url(s))
    else if ((s.startsWith("gh:") || s.startsWith("github:")) && s.contains("/")) {

      val s0 =
        if (s.startsWith("gh:")) s.stripPrefix("gh:")
        else s.stripPrefix("github:")

      val (orgName, path) = s0.split(":", 2) match {
        case Array(orgName0, path0) =>
          (orgName0, path0)
        case Array(orgName0) =>
          (orgName0, defaultGhPath)
      }

      val orgNameBranchOrError = orgName.split("/", 3) match {
        case Array(org0, name0)          => Right((org0, name0, defaultGhBranch))
        case Array(org0, name0, branch0) => Right((org0, name0, branch0))
        case _                           => Left(s"Malformed github channel '$s'")
      }

      orgNameBranchOrError.map {
        case (org, name, branch) =>
          val path0 =
            if (path.endsWith("/"))
              path + defaultGhFileName
            else
              path
          val url = ghUrl(org, name, branch, path0)
          FromUrl(url)
      }
    }
    else if (s.contains(":")) {
      val hasVersion = s.split(':').count(_.nonEmpty) >= 3
      if (hasVersion)
        DependencyParser.javaOrScalaDependencyParams(s).flatMap {
          case (j: JavaOrScalaDependency.JavaDependency, _) =>
            Right(JvmChannel.module(j.module.module, j.version))
          case (s: JavaOrScalaDependency.ScalaDependency, _) =>
            Left(s"Scala dependencies ($s) not accepted as JVM channels")
        }
      else
        ModuleParser.javaOrScalaModule(s).flatMap {
          case j: JavaOrScalaModule.JavaModule => Right(JvmChannel.module(j.module))
          case s: JavaOrScalaModule.ScalaModule =>
            Left(s"Scala dependencies ($s) not accepted as JVM channels")
        }
    }
    else
      Right(FromFile(fs.getPath(s).toAbsolutePath))

}
