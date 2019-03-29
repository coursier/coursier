package coursier.cache.internal

import java.io.{File, FileInputStream}
import java.util.Properties

import coursier.cache.Credentials

import scala.collection.JavaConverters._

abstract class CredentialFileHelpers {

  def path: String
  def optional: Boolean

  def read(): Seq[Credentials] = {

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
        .filter(_.endsWith(".username")).toVector

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

        Credentials(realmOpt, host, user, password)
      }

    } else if (optional)
      Nil
    else {
      throw new Exception(s"Credential file $path not found")
    }
  }

}
