package coursier.cli.install

import java.util.regex.Pattern.quote

import coursier.core.Module
import coursier.parse.{JavaOrScalaModule, ModuleParser}

abstract class Channel extends Product with Serializable {
  def repr: String
}

object Channel {

  final case class FromModule(module: Module) extends Channel {
    def repr: String =
      module.repr
  }

  final case class FromUrl(url: String) extends Channel {
    def repr: String =
      url
  }

  def module(module: Module): FromModule =
    FromModule(module)

  private lazy val ghUrlMatcher =
    (quote("https://github.com/") + "([^/]*)/([^/]*)" + quote("/blob/") + "([^/]*)" + quote("/") + "(.*)").r.pattern

  def url(url: String): FromUrl = {

    val m = ghUrlMatcher.matcher(url)

    val url0 =
      if (m.matches()) {
        val org = m.group(1)
        val name = m.group(2)
        val branch = m.group(3)
        val path = m.group(4)
        s"https://raw.githubusercontent.com/$org/$name/$branch/$path"
      } else
        url

    // https://github.com/coursier/apps/blob/master/apps/resources/ammonite.json
    // https://raw.githubusercontent.com/coursier/apps/master/apps/resources/ammonite.json

    FromUrl(url0)
  }

  def parse(s: String): Either[String, Channel] =
    if (s.contains("://"))
      Right(Channel.url(s))
    else
      ModuleParser.javaOrScalaModule(s)
        .right.flatMap {
          case j: JavaOrScalaModule.JavaModule => Right(Channel.module(j.module))
          case s: JavaOrScalaModule.ScalaModule => Left(s"Scala dependencies ($s) not accepted as channels")
        }

}
