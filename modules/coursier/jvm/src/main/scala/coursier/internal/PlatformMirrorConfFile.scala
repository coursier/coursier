package coursier.internal

import java.io.{File, FileInputStream}
import java.util.Properties

import coursier.params.{MavenMirror, Mirror, TreeMirror}

import scala.collection.JavaConverters._

abstract class PlatformMirrorConfFile {

  def path: String
  def optional: Boolean

  def mirrors(): Seq[Mirror] = {
    val f = new File(path)

    if (f.isFile) {
      val props = new Properties

      var fis: FileInputStream = null
      try {
        fis = new FileInputStream(f)
        props.load(fis)
      } finally {
        if (fis != null)
          fis.close()
      }

      val toProps = props
        .propertyNames()
        .asScala
        .map(_.asInstanceOf[String])
        .filter(_.endsWith(".to"))
        .toVector

      toProps.map { toProp =>
        val prefix = toProp.stripSuffix(".to")

        val to = props.getProperty(toProp)
        val from = Option(props.getProperty(s"$prefix.from")).getOrElse {
          throw new Exception(s"Property $prefix.from not found in $path")
        }

        val isTree = Option(props.getProperty(s"$prefix.type"))
          .forall {
            case "tree" =>
              true
            case "maven" =>
              false
            case _ =>
              throw new Exception(s"Invalid value for property $prefix.type in $path")
          }

        val froms = from.split(';')

        if (isTree)
          TreeMirror(to, froms.head, froms.tail.toSeq: _*)
        else
          MavenMirror(to, froms.head, froms.tail.toSeq: _*)
      }
    } else if (optional)
      Nil
    else
      throw new Exception(s"Credential file $path not found")
  }
}
