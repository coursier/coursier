package coursier.install

import java.io.File
import java.nio.file.{FileSystem, FileSystems, Path}
import java.util.regex.Pattern.quote

import coursier.core.Module
import coursier.parse.{DependencyParser, JavaOrScalaDependency, JavaOrScalaModule, ModuleParser}
import coursier.version.VersionConstraint
import dataclass.data

sealed abstract class Channel extends Product with Serializable {
  def repr: String
}

object Channel {

  @data class FromModule(
    module: Module,
    versionConstraint: VersionConstraint = VersionConstraint("latest.release")
  ) extends Channel {

    @deprecated("Use the override accepting a VersionConstraint instead", "2.1.25")
    def this(
      module: Module,
      version: String
    ) = this(
      module,
      VersionConstraint(version)
    )

    @deprecated("Use versionConstraint instead", "2.1.25")
    def version: String =
      versionConstraint.asString
    @deprecated("Use withVersionConstraint instead", "2.1.25")
    def withVersion(newVersion: String): FromModule =
      withVersionConstraint(VersionConstraint(newVersion))

    def repr: String =
      module.repr
  }

  object FromModule {
    @deprecated("Use the override accepting a VersionConstraint instead", "2.1.25")
    def apply(
      module: Module,
      version: String
    ): FromModule = apply(
      module,
      VersionConstraint(version)
    )
  }

  @data class FromUrl(url: String) extends Channel {
    def repr: String =
      url
  }

  @data class FromDirectory(path: Path) extends Channel {
    def repr: String =
      path.toString
  }

  @data class Inline() extends Channel {
    def repr: String =
      "inline"
  }

  def module(module: Module): FromModule =
    FromModule(module)
  def module(module: Module, version: VersionConstraint): FromModule =
    FromModule(module, version)
  @deprecated("Use the override accepting a VersionConstraint instead", "2.1.25")
  def module(module0: Module, version: String): FromModule =
    module(module0, VersionConstraint(version))

  private lazy val ghUrlMatcher = (
    quote("https://github.com/") +
      "([^/]*)/([^/]*)" +
      quote("/blob/") +
      "([^/]*)" +
      quote("/") +
      "(.*)"
  ).r.pattern

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

  def parse(s: String): Either[String, Channel] =
    parse(s, FileSystems.getDefault)

  def parse(s: String, fs: FileSystem): Either[String, Channel] =
    if (s == "inline")
      Right(Inline())
    else if (s.contains("://"))
      Right(Channel.url(s))
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
    else if (s.contains(":") && !s.contains(File.separator) && !s.contains("/")) {
      val hasVersion = s.split(':').count(_.nonEmpty) >= 3
      if (hasVersion)
        DependencyParser.javaOrScalaDependencyParams(s).flatMap {
          case (j: JavaOrScalaDependency.JavaDependency, _) =>
            Right(Channel.module(j.module.module, j.versionConstraint))
          case (s: JavaOrScalaDependency.ScalaDependency, _) =>
            Left(s"Scala dependencies ($s) not accepted as channels")
        }
      else
        ModuleParser.javaOrScalaModule(s).flatMap {
          case j: JavaOrScalaModule.JavaModule  => Right(Channel.module(j.module))
          case s: JavaOrScalaModule.ScalaModule =>
            Left(s"Scala dependencies ($s) not accepted as channels")
        }
    }
    else
      Right(FromDirectory(fs.getPath(s).toAbsolutePath))

}
