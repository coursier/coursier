package coursier.internal

import java.io.File

import coursier.params.{MavenMirror, Mirror, TreeMirror}

import scala.jdk.CollectionConverters._

abstract class PlatformMirrorConfFile {

  def path: String
  def optional: Boolean

  def mirrors(): Seq[Mirror] =
    mirrorsAsStandard().map(_.mirror)

  def mirrorsAsStandard(): Seq[Mirror.StandardMirror] = {

    val f = new File(path)

    if (f.isFile)
      coursier.paths.Mirror.parse(new File(path))
        .asScala
        .iterator
        .map { m =>
          m.`type`() match {
            case coursier.paths.Mirror.Types.MAVEN =>
              Mirror.StandardMirror.Maven(MavenMirror(m.from().asScala.toVector, m.to()))
            case coursier.paths.Mirror.Types.TREE =>
              Mirror.StandardMirror.Tree(TreeMirror(m.from().asScala.toVector, m.to()))
            case other =>
              sys.error(s"Unrecognized mirror type $other")
          }
        }
        .toVector
    else if (optional)
      Nil
    else
      throw new Exception(s"Credential file $path not found")
  }
}
