package coursier.credentials

import java.io.{File, FileInputStream}
import java.util.Properties

import scala.collection.JavaConverters._

final class FileCredentials private(
  val path: String,
  val optional: Boolean
) extends Serializable {

  private def this(path: String) = this(path, true)

  override def equals(o: Any): Boolean = o match {
    case x: FileCredentials => (this.path == x.path) && (this.optional == x.optional)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "coursier.credentials.CredentialFile".##) + path.##) + optional.##)
  }
  override def toString: String = {
    "CredentialFile(" + path + ", " + optional + ")"
  }
  private[this] def copy(path: String = path, optional: Boolean = optional): FileCredentials = {
    new FileCredentials(path, optional)
  }
  def withPath(path: String): FileCredentials = {
    copy(path = path)
  }
  def withOptional(optional: Boolean): FileCredentials = {
    copy(optional = optional)
  }

  def read(): Seq[DirectCredentials] = {

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

      val userProps = props
        .propertyNames()
        .asScala
        .map(_.asInstanceOf[String])
        .filter(_.endsWith(".username"))
        .toVector

      userProps.map { userProp =>
        val prefix = userProp.stripSuffix(".username")

        val user = props.getProperty(userProp)
        val password = Option(props.getProperty(s"$prefix.password")).getOrElse {
          throw new Exception(s"Property $prefix.password not found in $path")
        }

        val host = Option(props.getProperty(s"$prefix.host")).getOrElse {
          throw new Exception(s"Property $prefix.host not found in $path")
        }

        val realmOpt = Option(props.getProperty(s"$prefix.realm")) // filter if empty?

        DirectCredentials(host, user, password)
          .withRealm(realmOpt)
      }

    } else if (optional)
      Nil
    else
      throw new Exception(s"Credential file $path not found")
  }
}
object FileCredentials {

  def apply(path: String): FileCredentials = new FileCredentials(path)
  def apply(path: String, optional: Boolean): FileCredentials = new FileCredentials(path, optional)
}
