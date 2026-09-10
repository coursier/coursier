package coursier.maven

import coursier.core.compatibility.xmlParseDom
import coursier.util.Xml
import coursier.util.Traverse.TraverseOps

import dataclass.data

/** The parts of a Maven `settings.xml` file coursier makes use of
  *
  * Sections that have no equivalent in coursier, like `profiles` or `pluginGroups`, are ignored.
  */
@data(setters = false) case class MavenSettings(
  mirrors: Seq[MavenSettings.Mirror] = Nil,
  servers: Seq[MavenSettings.Server] = Nil
) {

  /** The `server` element whose `id` is `id`, if any */
  def server(id: String): Option[MavenSettings.Server] =
    servers.find(_.id == id)
}

object MavenSettings {

  /** A `mirror` element of a Maven `settings.xml` file
    *
    * @param mirrorOf
    *   the raw content of the `mirrorOf` element, like `"*"`, `"central"`, or
    *   `"external:*,!my-repo"`
    * @param mirrorOfLayouts
    *   the raw content of the `mirrorOfLayouts` element - empty if that element is missing, which
    *   Maven takes to mean `"default,legacy"`
    * @param blocked
    *   whether this mirror blocks the repositories it matches, rather than mirroring them
    */
  @data(setters = false) case class Mirror(
    id: String,
    url: String,
    mirrorOf: String,
    mirrorOfLayouts: String = "",
    blocked: Boolean = false
  )

  /** A `server` element of a Maven `settings.xml` file
    *
    * Only the fields coursier can pass along as HTTP credentials are read.
    */
  @data(setters = false) case class Server(
    id: String,
    username: Option[String] = None,
    password: Option[String] = None
  )

  private def elements(node: Xml.Node, label: String): Seq[Xml.Node] =
    node.children.filter(child => child.isElement && child.label == label)

  private def sectionElements(node: Xml.Node, section: String, label: String): Seq[Xml.Node] =
    elements(node, section).flatMap(elements(_, label))

  private def textOpt(node: Xml.Node, label: String): Option[String] =
    elements(node, label)
      .headOption
      .map(_.textContent.trim)
      .filter(_.nonEmpty)

  private def mirror(node: Xml.Node): Either[String, Mirror] = {
    val idOpt   = textOpt(node, "id")
    val descr   = idOpt.fold("mirror")(id => s"mirror '$id'")
    val blocked = textOpt(node, "blocked").exists(_.equalsIgnoreCase("true"))
    for {
      url      <- textOpt(node, "url").toRight(s"No url found in $descr")
      mirrorOf <- textOpt(node, "mirrorOf").toRight(s"No mirrorOf found in $descr")
    } yield Mirror(
      idOpt.getOrElse(""),
      url,
      mirrorOf,
      textOpt(node, "mirrorOfLayouts").getOrElse(""),
      blocked
    )
  }

  private def server(node: Xml.Node): Either[String, Server] =
    textOpt(node, "id")
      .toRight("No id found in server")
      .map { id =>
        Server(id, textOpt(node, "username"), textOpt(node, "password"))
      }

  def fromNode(node: Xml.Node): Either[String, MavenSettings] =
    if (node.label == "settings")
      for {
        mirrors <- sectionElements(node, "mirrors", "mirror").eitherTraverse(mirror)
        servers <- sectionElements(node, "servers", "server").eitherTraverse(server)
      } yield MavenSettings(mirrors, servers)
    else
      Left(s"Expected a settings element at the root, got '${node.label}'")

  /** Parses the content of a Maven `settings.xml` file */
  def parse(content: String): Either[String, MavenSettings] =
    xmlParseDom(content).flatMap(fromNode)
}
