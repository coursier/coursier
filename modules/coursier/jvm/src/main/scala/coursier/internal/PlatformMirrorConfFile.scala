package coursier.internal

import java.io.File

import coursier.params.{MavenMirror, Mirror, TreeMirror}

import scala.collection.JavaConverters._

abstract class PlatformMirrorConfFile {

  def path: String
  def optional: Boolean

  def mirrors(): Seq[Mirror] = {

    val f = new File(path)

    if (f.isFile)
      coursier.paths.Mirror.parse(new File(path))
        .asScala
        .iterator
        .map { m =>
          m.`type`() match {
            case coursier.paths.Mirror.Types.MAVEN =>
              MavenMirror(m.from().asScala.toVector, m.to())
            case coursier.paths.Mirror.Types.TREE =>
              TreeMirror(m.from().asScala.toVector, m.to())
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
